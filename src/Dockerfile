# Use an official Python runtime as a parent image
FROM fedora

# Set the working directory to /app
WORKDIR /app

# Copy the current directory contents into the container at /app
ADD . /app

# Install any needed packages specified in requirements.txt
RUN dnf -y install java
RUN dnf -y install java-devel
RUN dnf -y install net-tools
RUN dnf -y install git
RUN git clone https://github.com/EldinTuru/CS455-p3.git
WORKDIR "CS455-p3/src"
RUN pwd
RUN javac -cp '.:../commons-cli-1.4/commons-cli-1.4.jar' *.java
RUN rmiregistry 1099 &

# Make port 1099 available to the world outside this container
EXPOSE 1099

# Define environment variable
ENV NAME World

# Run app.py when the container launches
CMD ["java", "IdServer", "--numport", "1099", "--verbose"]

