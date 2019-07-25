
/*
 * CS 475 HW1: Shell
 * http://www.jonbell.net/gmu-cs-475-spring-2018/homework-1/
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <ctype.h>

//global variables 
static char **history;             // storing history
static int lengthOfHistory;        // current number of history
static char *input;                // input from user

/* Constants */
#define BUFFER_SIZE 1024      // max number of buffer
#define MAX_AGRS    129      // max number of arguments
#define MAX_HISTORY 100       // max number of elements in the history list

/* Type declarations */
typedef struct{                // a parsed command 
    int argc;                  // number of arguments
    char *argv[MAX_AGRS];      // the arguments
    int fileds[2];             // input/output 
    char *name;                // name of the command 
}command;


typedef struct{               // struct to store a commands
    int cmd_count;            // number of commands
    command *cmds[];          // he commands themselves - context
}commands;


// useful for keeping track of parent's prev call for cleanup 
static command *commandPrevious;
static commands *commandPreviouss;
static char *temp_line;

static const char * checkHistory = "history";

char *getInput();
void freeMemoryQuit(int);
int allBlank(char *);
int emptyHistory();
int addHistory(char *);
int historyCommand(commands *, command *);
void cleanup();
int execCommand(commands*, command*, int (*)[2]);
int isHistory(char *);
int execCommands(commands *);
void cleanCommands(commands *);
void closeCommands(int (*)[2], int);
command *parseCommandSingle(char *);
commands *parseCommandMore(char *);
int checkBuiltin(command *);
int emptyHistory();
int solveBuiltin(commands *, command *);


int main()
{
    int checkExit;

    while (1) {
        fflush(stdin);
        fflush(stdout);
        printf("475$");
        fflush(stdout);

        input = getInput();

        if (input == NULL) {
            //On the input EOF (ctrl-d)
            freeMemoryQuit(EXIT_SUCCESS);
        }

        if (strlen(input) > 0 && !allBlank(input)) {

            // add commands to history if the command isn't history 
            if (!isHistory(input))
                addHistory(input);

            commands *commands = parseCommandMore(input);
            //if there are more than 127 arguments
            if(commands->cmds[0]->argc >= MAX_AGRS)
            {
                fprintf(stderr, "error: too many arguments\n");
                cleanCommands(commands);
                free(input);
                continue;
            }

            checkExit = execCommands(commands);
            cleanCommands(commands);
        }

        free(input);

        // get ready to exit 
        if (checkExit == -1)
            break;
    }

    // perform cleanup to ensure no leaks
    freeMemoryQuit(EXIT_SUCCESS);
    return 0;
}


//check the input is all black or not 
int allBlank(char *input)
{
    int length = strlen(input);

    for (int i = 0; i < length; i++) {
        if (input[i]!=' ')
            return 0;
    }
    return 1;
}

// whether a command is a history command 
int isHistory(char *input)
{
    for (int i = 0; i < (int) strlen(checkHistory); i++) {
        if (input[i] != checkHistory[i])
            return 0;
    }
    return 1;
}

/* Returns a pointer to a input entered by user.
 * the caller is responsible for freeing up the memory
 */
char * getInput()
{
    int buf_size = BUFFER_SIZE;
    char *input = malloc(buf_size * sizeof(char));

    if (input == NULL) {
        fprintf(stderr, "error: malloc failed\n");
        freeMemoryQuit(EXIT_FAILURE);
    }

    int count = 0;
    char temp;

    temp = getchar();
    while (temp != '\n') 
    {
        //enter ctrl+d 
        if (temp == EOF) 
        {
            free(input);
            return NULL;
        }

        // allocate more memory
        if (count >= buf_size) 
        {
            buf_size = 2 * buf_size;
            input = realloc(input, buf_size);
        }

        input[count++] = temp;
        temp = getchar();
    }

    input[count] = '\0';
    return input;
}

// Clears the history
int emptyHistory()
{
    for (int i = 0; i < lengthOfHistory; i++)
        free(history[i]);
    lengthOfHistory = 0;
    return 0;
}

// whether a command is a built-in
// one of [exit, cd, history]

int checkBuiltin(command *cmd)
{

    if (strcmp(cmd->name, "exit") == 0)
        return 1;

    if (strcmp(cmd->name, "cd") == 0)
        return 1;

    if (strcmp(cmd->name, "history") == 0)
        return 1;

    return 0;
}

// closes all the commands
void closeCommands(int (*commands)[2], int command_count)
{
        close(commands[0][0]);
        close(commands[0][1]);
}

// Frees up memory for the commands
void cleanCommands(commands *cmds)
{
    free(cmds->cmds[0]);
    free(cmds);
}


// cleans up history before exits
void freeMemoryQuit(int status)
{
    emptyHistory();
    free(history);
    exit(status);
}

// handling the history command 
int historyCommand(commands *cmds, command *cmd)
{
    //print history 
    if (cmd->argc == 1) {
        int i;

        for (i = 0; i < lengthOfHistory ; i++) 
        {
            // write to a file descriptor - output_fd
            printf("%d: %s\n", i, history[i]);
        }
        return 1;
    }
    if (cmd->argc > 1) {
        // clear history
        if (strcmp(cmd->argv[1], "-c") == 0) 
        {
            emptyHistory();
            return 0;
        }

        // exec command from history 
        char *final;
        long long_offset;
        int temp_offset;

        long_offset = strtol(cmd->argv[1], &final, 10);
        if (final == cmd->argv[1]) 
        {
            fprintf(stderr, "error: cannot convert\n");
            return 1;
        }

        temp_offset = (int) long_offset;
        if (temp_offset > lengthOfHistory) 
        {
            fprintf(stderr, "error: offset > items\n");
            return 1;
        }

        /* parse execute command */
        char *line = strdup(history[temp_offset]);

        if (line == NULL)
            return 1;

        // add new commands to history if the command isn't history 
        if (!isHistory(line))
            addHistory(line);

        commands *new_commands = parseCommandMore(line);

        /* set pointers so that these can be freed when
         * child processes die during execution
         */
        commandPrevious = cmd;
        temp_line = line;
        commandPreviouss = cmds;

        execCommands(new_commands);
        cleanCommands(new_commands);
        free(line);

        /* reset */
        commandPrevious = NULL;
        temp_line = NULL;
        commandPreviouss = NULL;

        return 0;
    }
    return 0;
}

// Parses a single command into a command struct.
command *parseCommandSingle(char *input)
{
    int tokenCount = 0;
    char *token;

    /* allocate memory for the cmd structure */
    command *temp = calloc(sizeof(command) + BUFFER_SIZE * sizeof(char *), 1);

    //caller is responsible for freeing up the memory
    if (temp == NULL) 
    {
        fprintf(stderr, "error: alloc error\n");
        exit(EXIT_FAILURE);
    }

    // get token by splitting on whitespace
    token = strtok(input, " ");

    while (token != NULL && tokenCount < BUFFER_SIZE) 
    {
        temp->argv[tokenCount++] = token;
        token = strtok(NULL, " ");
    }
    temp->name = temp->argv[0];
    temp->argc = tokenCount;
    return temp;
}

//Parses a command into a commands* structure.
commands *parseCommandMore(char *input)
{
    char *c = input;
    commands *cmds;

    cmds = calloc(sizeof(commands) +
              1 * sizeof(command *), 1);

    if (cmds == NULL) 
    {
        fprintf(stderr, "error: memory alloc error\n");
        exit(EXIT_FAILURE);
    }

    
    cmds->cmds[0] = parseCommandSingle(c);
    cmds->cmd_count = 1;
    return cmds;
}

// Executes a set of commands 
int execCommands(commands *cmds)
{
    int execReturn;

    // single command
    if (cmds->cmd_count == 1) 
    {
        cmds->cmds[0]->fileds[STDIN_FILENO] = STDIN_FILENO;
        cmds->cmds[0]->fileds[STDOUT_FILENO] = STDOUT_FILENO;
        execReturn = execCommand(cmds, cmds->cmds[0], NULL);
        wait(NULL);
    } 

    return execReturn;
}

// Executes a command by forking of a child and calling exec.
// until the child is done executing.
int execCommand(commands *cmds, command *cmd, int (*cmd_temp)[2])
{
    if (checkBuiltin(cmd) == 1)
        return solveBuiltin(cmds, cmd);

    pid_t child = fork();
    if (child == -1) 
    {
        fprintf(stderr, "error: fork error\n");
        return 0;
    }

    // in the child
    if (child == 0) 
    {

        int fdInput = cmd->fileds[0];
        int fdOutput = cmd->fileds[1];
        // change input/output file descriptors if they aren't standard
        if (fdInput != -1 && fdInput != STDIN_FILENO)
            dup2(fdInput, STDIN_FILENO);

        if (fdOutput != -1 && fdOutput != STDOUT_FILENO)
            dup2(fdOutput, STDOUT_FILENO);

        if (cmd_temp != NULL) 
        {
            int cmd_count = cmds->cmd_count - 1;
            closeCommands(cmd_temp, cmd_count);
        }

        // execute the command
        execv(cmd->name, cmd->argv);
        //error occurs 
        // fprintf(stderr, "error: %s\n", strerror(errno));

        // cleanup in the child to a memory leaks 
        emptyHistory();
        free(history);
        free(cmd_temp);
        free(input);
        cleanCommands(cmds);

        if (commandPrevious != NULL) 
        {
            free(commandPrevious);
            free(temp_line);
            free(commandPreviouss);
        }


        //exit from child function
        _exit(EXIT_FAILURE);
    }
    // parent function continues here 
    return child;
}

// Adds the user's input to the history. 
// wheneverthe number of items reaches max number of items.
// For a few 100 items, this works well and easy to reason about
 
int addHistory(char *input)
{
    // initialize on first call

    if (history == NULL) 
    {

        history = calloc(sizeof(char *) * MAX_HISTORY, 1);
        if (history == NULL) 
        {
            fprintf(stderr, "error: alloc error\n");
            return 0;
        }
    }

    // make a copy of the input 
    char *copyLine;

    copyLine = strdup(input);
    if (copyLine == NULL)
        return 0;

    //move the old contents to a previous position, and decrement len
    if (lengthOfHistory == MAX_HISTORY) 
    {
        free(history[0]);
        int spaceMove = sizeof(char *) * (MAX_HISTORY - 1);

        memmove(history, history+1, spaceMove);

        if (history == NULL) {
            fprintf(stderr, "error: memory alloc error\n");
            return 0;
        }
        lengthOfHistory--;
    }

    history[lengthOfHistory++] = copyLine;
    return 1;
}

// the shell built-in comamnds. 
//Takes the input/output file descriptors
//Returns -1 to indicate that program should exit.

int solveBuiltin(commands *cmds, command *cmd)
{
    int check;
    //exit command 
    if (strcmp(cmd->name, "exit") == 0)
        return -1;
    //cd command 
    if (strcmp(cmd->name, "cd") == 0) {
        check = chdir(cmd->argv[1]);
        if (check != 0) {

            fprintf(stderr, "error: No such file or directory\n");
            return 1;
        }
        return 0;
    }
    //history command 
    if (strcmp(cmd->name, "history") == 0)
        return historyCommand(cmds, cmd);

    return 0;
}



