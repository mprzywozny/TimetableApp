#Timetable System

This Java-based Timetable Scheduling System enables the creation, management, and visualisation of weekly lecture schedules through a graphical client-server architecture using JavaFX.

Features:
  -Client-Server Communication: The client connects to a multithreaded Java server over sockets for real-time
  operations.
  -Lecture Management: Users can add, remove, or undo changes to scheduled lectures.
  -Timetable View: Displays a dynamic weekly timetable with export/import CSV functionality.
  -History Tracking: Logs every change for transparency and audit.
  -Statistics Module: Visualizes class hours distribution using bar charts.
  -Dark/Light Mode & Fullscreen Support: Customizable user interface for improved UX.
  -Robust CSV Import: Validates and parses structured timetable data with error checking.

Technologies Used:
  -JavaFX for GUI
  -Java Sockets for networking
  -Java Collections & Concurrency for state management
  -CSV I/O for data persistence
  
How to Run:
  -Start the server: TimetableServer.java
  -Launch the client: TimetableClient.java
  -Use the UI to log in and manage lectures.
