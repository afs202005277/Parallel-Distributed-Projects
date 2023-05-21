## Running instructions:
To run our project, you can simply execute the `compile.bat` file present in the `src` folder.
This file will delete all the .class files present in the folder and use javac to compile all the classes and dependencies of the project.

After compilation, you can just call `java Server` in the terminal and the Server will start.
Secondly, you can run any number of Client instances with `java Client`. These instances will automatically connect to the server.
Finally, for usage instructions, you can use the `help` command in the Client terminal.

The default setup of the server is to run a maximum of 2 game instances simultaneously, with 2 players in each game. 