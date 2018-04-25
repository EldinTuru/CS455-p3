##########################################
OUR VIDEO IS LOCATED AT: https://youtu.be/blbiHe6WpR0 
###########################################

# Project Number/Title {#mainpage}

* Project Number 2: Identity Server (part 1)
* Author: Eldin Turulja, Edin Hanic (Team 21)
* Class: CS455

## Overview

This project creates a server that will serve RMI calls from multiple clients.
It allows clients to create accounts through these RMI calls.

## Manifest

A listing of source files and other non-generated files and a brief (one line)
explanation of the purpose of each file.

* IdClient.java - represents client, allows for calls to be made to the server
* IdServer.java - represents server, holds all the logic for the RMI calls
* Service.java - The interface that the server implements
* User.java - represents a user in our database 
* README.md - this file


## Building the project

Go to the directory this README file is located in. Execute this command:

	$ make

This will compile all of the java files.

## Features and usage

Before running any commands you must set up your RMI server

	$ rmiregistry <port#> & 

After building you may start the server with the following command:

	$ java IdServer --numport <port#> --verbose &

After starting the server we can start many clients using this command:

	$ java -cp '.:../commons-cli-1.4/commons-cli-1.4.jar' IdClient --server <serverhosts> [--numport <port#] <query>
	$ java -cp '.:../commons-cli-1.4/commons-cli-1.4.jar' IdClient --server 172.17.0.2 1099 --get users

You can fill the query at the top with any of the options below:
--create <loginname> [<real name>] [--password <password>]
--lookup <loginname>
--reverse-lookup <UUID>
--modify <oldloginname> <newloginname> [--password <password>]
--delete <loginname> [-password <password>]
--get users|uuids|all

Any of the above options can be abbreviated as -s, -n, -c, -l, -r, -m, -d, -p.

## Testing

We did a lot of manual testing for the functionality between the server and client.

We also decided to add a "stress test" of sorts. We created a bash script that looped
through a number the user passed in, and it executed all of our "CRUD" functions
asynchronously by running them in the background. Before running the script run the 
rmiregistry with the following command:

	$ rmiregistry <port> & 

Also run the server before running the script:

	$ java IdServer [--numport <port#>] [--verbose]

To run the script use this command:

	$ ./stress_test <# number of users> <port#>

### Known Bugs

To the best of our knowledge and testing there are no known bugs.

## Discussion

We took a large portion of the RMI parts of the project from the examples from class.
After that Edin used a libarary to parse through all of the commands on the client side,
allowing for easier calling of the server functionality. The library was very easy to use
and it saved a ton of time on handling all of the potential parameters. That hint in the
assignment was very helpful.

We had some trouble at first setting up the RMI service but it could have been a problem
with the laptop we were working on at the time. When done on a desktop it was much easier,
and it worked by simply following the steps in the README.

We decided to do store users in a hashmap in the server and then serialize it into a local
file. This is constantly done in the background on a consistent time interval. It is also
"saved" when the server is shutdown, we did this with the hook that we used in the previous
project.

The SLL part was a little tricky at first but we decided to just keep the certificate local,
that way we didn't have to worry about creating the cert everytime a new user wanted to
connect to our server. The only tricky part was where we should store the key for our
encryption.

The verbose option was simple to add as it was just like normal logging. We didn't use a
logging library but instead have a method in our code to check if the server is running
in verbose mode and if it is, it will print extra messages.

As for roles, Edin did most of the client. We worked on parts of the server together, and
things like SSL encryption. Eldin was responsible for most of the testing.

## Sources used

