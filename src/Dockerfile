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

# Make port 80 available to the world outside this container
EXPOSE 80

# Define environment variable
ENV NAME World

# Run app.py when the container launches
CMD ["java", "tcp.multithreaded.TimeServer", "5005"]

