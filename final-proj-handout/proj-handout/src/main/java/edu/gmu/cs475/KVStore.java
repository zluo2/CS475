package edu.gmu.cs475;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.nodes.PersistentNode;
import org.apache.curator.framework.recipes.leader.LeaderLatch;
import org.apache.curator.framework.recipes.leader.LeaderLatchListener;
import org.apache.curator.framework.recipes.leader.Participant;
import org.apache.curator.retry.RetryOneTime;
import org.apache.zookeeper.CreateMode;
import org.apache.curator.framework.state.ConnectionState;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.io.IOException;
import java.rmi.RemoteException;

public class KVStore extends AbstractKVStore {

    private Map<String, String> cache = new ConcurrentHashMap<>();
    private Map<String, List<String>> mapKeyToFollowers = new ConcurrentHashMap<>();

    private PersistentNode myMembership;
    private LeaderLatch leaderLatch;
    // ReentrantReadWriteLock for thread-safe
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock rLock = rwl.readLock();
    private final Lock wLock = rwl.writeLock();
    
    Map lockMap=new Hashtable();

    private volatile boolean connected;
    private String leaderId;

    /*
    Do not change these constructors.
    Any code that you need to run when the client starts should go in initClient.
     */
    public KVStore(String zkConnectString) {
        super(zkConnectString);
    }

    public KVStore(String zkConnectString, int i1, int i2, int i3) {
        super(zkConnectString, i1, i2, i3);
    }


    /**
     * This callback is invoked once your client has started up and published an RMI endpoint.
     * <p>
     * In this callback, you will need to set-up your ZooKeeper connections, and then publish your
     * RMI endpoint into ZooKeeper (publishing the hostname and port)
     * <p>
     * You will also need to set up any listeners to track ZooKeeper events
     *
     * @param localClientHostname Your client's hostname, which other clients will use to contact you
     * @param localClientPort     Your client's port number, which other clients will use to contact you
     */
    @Override
    public void initClient(String localClientHostname, int localClientPort) {

        myMembership = new PersistentNode(zk, CreateMode.EPHEMERAL, 
            false, ZK_MEMBERSHIP_NODE + "/" + getLocalConnectString(), new byte[0]);
        myMembership.start();

        leaderLatch = new LeaderLatch(zk, ZK_LEADER_NODE, getLocalConnectString());
        leaderLatch.addListener(new LeaderLatchListener() {
            @Override
            public void isLeader() {
                System.out.println(getLocalConnectString() + " Leader");
            }

            @Override
            public void notLeader() {
                System.out.println(getLocalConnectString() + " Not leader");
            }
        });
        try {
            leaderLatch.start();
        } catch (Exception e) {
            // e.printStackTrace();
        }

        // TreeCache for tracking node membership
        TreeCache members = new TreeCache(zk, ZK_MEMBERSHIP_NODE);
        members.getListenable().addListener((client, event) -> {
            // System.out.println(getLocalConnectString() + " Membership change detected: "
            // + event);

            if(leaderId == null) {
                leaderId = leaderLatch.getLeader().getId();
            } 
            try {
                while(leaderLatch.getLeader().getId().isEmpty() || leaderLatch.getLeader().getId().equals("")) {
                    // wait until a leader has been elected
                }
                // When a node detects that the leader has changed 
                if(!leaderId.equals(leaderLatch.getLeader().getId())) {
                    leaderId = leaderLatch.getLeader().getId();
                    if(leaderLatch.hasLeadership()) {
                        // if this is a leader being promoted 
                        // after a prior leader failed or was disconnected, 
                        // then it will simply initialize itself
                    } else {   
                        // if it is not the leader
                        // it must flush its entire cache.
                        cache.clear();
                        mapKeyToFollowers.clear();
                    }
                }
            } catch (Exception e1) {

            }
        });

        try {
            members.start();
        } catch (Exception e) {
            // e.printStackTrace();
        }
    }

    /**
     * Retrieve the value of a key
     *
     * @param key
     * @return The value of the key or null if there is no such key
     * @throws IOException if this client or the leader is disconnected from ZooKeeper
     */
    @Override
    public String getValue(String key) throws IOException {
        // If node N does not currently hold a live ZooKeeper session
        // then it will throw an IOException for any read operation
        if(!connected) {
            throw new IOException();
        }
        String value = null;
        if(leaderLatch.hasLeadership()) {
            // if its leader, return the value
            value = cache.get(key);
        } else {
            // if its follower, check if cache contains key
            if(cache.containsKey(key)) {
                value = cache.get(key);
            } else {
                // if cache don't contain key
                // request value from the leader
                boolean reachLeader = false;
                // If node N is unable to contact the leader,
                // but ZooKeeper indicates that the leader and client are both still active in ZooKeeper,
                // then the client must wait for the leader to become available
                while(!reachLeader) {
                    try {
                        // if the leader does not have a live ZooKeeper session)
                        // node N will first participate in the leader election
                        // leaderId then gets updated
                        // and then complete the read
                        value = connectToKVStore(leaderId).getValue(key, getLocalConnectString());
                        if(value != null) {
                            // if value not null
                            // Update the local cached value
                            cache.put(key, value);
                        }
                        reachLeader = true;
                    } catch (Exception e) {
                        // e.printStackTrace();
                        System.out.println("Get cannot reach leader");
                    }
                }
            }
        }
        return value;
    }

    /**
     * Update the value of a key. After updating the value, this new value will be locally cached.
     *
     * @param key
     * @param value
     * @throws IOException if this client or the leader is disconnected from ZooKeeper
     */
    @Override
    public void setValue(String key, String value) throws IOException {
        // If node N does not currently hold a live ZooKeeper session
        // then it will throw an IOException for any write operation.
        if(!connected) {
            throw new IOException();
        }
        if(leaderLatch.hasLeadership()) {
            // if its leader
            synchronized(getCommon(key)) {
                // Notify all clients to invalidate their cache
                List<String> followerIDs = mapKeyToFollowers.get(key);
                if(followerIDs != null) {
                    boolean invalidateAll = false;
                    // If the leader is unable to contact a client with a cached version of that key:
                    // The leader must wait until all  invalidate messages are acknowledged. 
                    while(!invalidateAll) {
                        try {
                            // However, if a client becomes disconnected from ZooKeeper, 
                            // and the leader detects this, 
                            // it should ignore the failure of the  invalidate message
                            Collection<Participant> participants = leaderLatch.getParticipants();
                            ArrayList<String> participantIds = new ArrayList<>();
                            for(Participant participant : participants) {
                                participantIds.add(participant.getId());
                            }
                            for (String followerID : followerIDs) {
                                if(participantIds.contains(followerID)) {
                                    connectToKVStore(followerID).invalidateKey(key);
                                }
                            }
                            invalidateAll = true;
                        } catch (Exception e) {
                            // e.printStackTrace();
                            System.out.println("Can't invalidate followers");
                        }
                    }
                    // clear its list of clients which have this key cached
                    followerIDs.clear();
                }
            }
        } else {
            // follower will ask the leader to update the value
            boolean reachLeader = false;
            // If node N is not able to contact the leader, 
            // but ZooKeeper indicates that the leader and client are both still active in ZooKeeper, 
            // then the client must wait for the leader to become available 
            while(!reachLeader) {
                try {
                    // if the leader does not have a live ZooKeeper session)
                    // then it will first participate in a leader election
                    // leaderId then gets updated
                    // and then complete the write
                    connectToKVStore(leaderId).setValue(key, value, getLocalConnectString());
                    reachLeader = true;
                } catch (IOException e) {
                    throw new IOException();
                } catch (Exception e) {
                    // e.printStackTrace();
                    System.out.println("Set cannot reach leader");
                }
            }
        }
        // Update the local cached value
        cache.put(key, value);
    }

    /**
     * Request the value of a key. The node requesting this value is expected to cache it for subsequent reads.
     * <p>
     * This command should ONLY be called as a request to the leader.
     *
     * @param key    The key requested
     * @param fromID The ID of the client making the request (as returned by AbstractKVStore.getLocalConnectString())
     * @return The value of the key, or null if there is no value for this key
     * <p>
     * DOES NOT throw any exceptions (the RemoteException is thrown by RMI if the connection fails)
     */
    @Override
    public String getValue(String key, String fromID) throws RemoteException {
        String value = null;
        if(cache.containsKey(key)) {
            // if leader's cache contains the key
            // get the list of the followers who have the key cached
            List<String> followerIDs = mapKeyToFollowers.get(key);
            if(followerIDs == null) {
                followerIDs = new ArrayList<>();
            }
            // add the requesting follower to the follower list with key cached
            followerIDs.add(fromID);
            mapKeyToFollowers.put(key, followerIDs);
            // get the value
            value = cache.get(key);
        }
        return value;
    }

    /**
     * Request that the value of a key is updated. The node requesting this update is expected to cache it for subsequent reads.
     * <p>
     * This command should ONLY be called as a request to the leader.
     * <p>
     * This command must wait for any pending writes on the same key to be completed
     *
     * @param key    The key to update
     * @param value  The new value
     * @param fromID The ID of the client making the request (as returned by AbstractKVStore.getLocalConnectString())
     */
    @Override
    public void setValue(String key, String value, String fromID) throws IOException {
        // the leader is disconnected from ZooKeeper: 
        // If it finds it does not hold a valid ZooKeeper session, 
        // it must throw an IOException
        if(!connected) {
            throw new IOException();
        }
        synchronized(getCommon(key)) {
            // Notify all clients to invalidate their cache
            List<String> followerIDs = mapKeyToFollowers.get(key);
            if(followerIDs != null) {
                boolean invalidateAll = false;
                // If the leader is unable to contact a client with a cached version of that key:
                // The leader must wait until all  invalidate messages are acknowledged. 
                while(!invalidateAll) {
                    try {
                        // However, if a client becomes disconnected from ZooKeeper, 
                        // and the leader detects this, 
                        // it should ignore the failure of the  invalidate message
                        Collection<Participant> participants = leaderLatch.getParticipants();
                        ArrayList<String> participantIds = new ArrayList<>();
                        for(Participant participant : participants) {
                            participantIds.add(participant.getId());
                        }
                        for (String followerID : followerIDs) {
                            if(participantIds.contains(followerID)) {
                                connectToKVStore(followerID).invalidateKey(key);
                            }
                        }
                        invalidateAll = true;
                    } catch (Exception e) {
                        // e.printStackTrace();
                        System.out.println("Can't invalidate followers");
                    }
                }
                // clear its list of clients which have this key cached
                followerIDs.clear();
            } else {
                followerIDs = new ArrayList<>();
            }
           // Update the value
            cache.put(key, value);        
            // Add node N to the (now emptied) list of clients with this key cached
            followerIDs.add(fromID);
            mapKeyToFollowers.put(key, followerIDs);
        }
    }

    /**
     * Instruct a node to invalidate any cache of the specified key.
     * <p>
     * This method is called BY the LEADER, targeting each of the clients that has cached this key.
     *
     * @param key key to invalidate
     *            <p>
     *            DOES NOT throw any exceptions (the RemoteException is thrown by RMI if the connection fails)
     */
    @Override
    public void invalidateKey(String key) throws RemoteException {
    	//String newKey = key.intern();
        synchronized(getCommon(key)) {
            cache.remove(key);
        }
        
        synchronized(lockMap) {
        	lockMap.remove(key);
        }
    }

    /**
     * Called when ZooKeeper detects that your connection status changes
     * @param curatorFramework
     * @param connectionState
     */
    @Override
    public void stateChanged(CuratorFramework curatorFramework, ConnectionState connectionState) {
        // System.out.println(getLocalConnectString() + " connection state changed: " + connectionState);
        if(connectionState==ConnectionState.CONNECTED) {
            connected = true;
        } else if(connectionState==ConnectionState.RECONNECTED) {
            // When a node is reconnected to a quorum of ZooKeepers, 
            // it will check to see how many other nodes are there. 
            // If there are none, 
            // it will perform the leader election and then assume a leader role, 
            // and can initialize itself using any cached data that it has. 
            // If, however, there are other nodes present at the moment that it reconnects (regardless of who the leader is/was), 
            // the node will flush its entire local cache, 
            // including all values and cache information.
            try {
                if (leaderLatch.getParticipants().size() > 1) {
                    cache.clear();
                    mapKeyToFollowers.clear();
                }
                connected = true;          
            } catch (Exception e) {

            }
        } else if(connectionState==ConnectionState.LOST||connectionState==ConnectionState.SUSPENDED) {
            connected = false;
        }
    }

    /**
     * Release any ZooKeeper resources that you setup here
     * (The connection to ZooKeeper itself is automatically cleaned up for you)
     */
    @Override
    protected void _cleanup() {
        try {
            if(leaderLatch.getState()==LeaderLatch.State.STARTED) {
                leaderLatch.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // copy the code from https://justavo.wordpress.com/2004/09/18/stringintern-and-synchronization/
    // author: justavo
    // date: May 7, 2018
    //synchronize on a common single object for all the intances of Strings that are Object.equals(Object) among 
    //them (you need to synchronize concurrent access to the system for the same user). 
    //Also it’s very common to choose this common single object as the value of a Hashtable 
    //with the keySet being the String values.
    private Object getCommon(String value) {
        synchronized(lockMap) {
            Object common=lockMap.get(value);
            if(common==null) {
                common=new Object();
                lockMap.put(value,common);
            }
            return common;
        }

    }

}
