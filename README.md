# ChatterBox
![logo](https://github.com/user-attachments/assets/9ddb6fdb-cb78-49b4-8d23-2b0dbe344089)

## Academic Research Project

**IMPORTANT:** This application is developed strictly for academic research purposes as part of a thesis project. All testing is conducted in a controlled environment. The code and software are not intended for deployment outside of research contexts.

Show Image

## Overview

ChatterBox is an Android application designed to demonstrate and research various data collection methodologies and user interaction patterns on mobile devices. The project serves as a technical study for identifying potential privacy and security concerns in mobile applications.

The application presents a forum and messaging interface while also implementing advanced data collection capabilities for research purposes. All data collected during testing is used solely for academic research and remains confidential within the study environment.

## Features

### User Interface Components

- User authentication and profile management
- Forum-based discussions with post creation and commenting
- Private messaging between users
- Role-based access control (Student/Teacher roles)
- Media sharing capabilities
- Location sharing in messages

### Research Data Collection Modules

- User interaction patterns analysis
- Input monitoring for user experience research
- Notification content analysis
- Background activity monitoring
- Screen content analysis
- Media metadata collection
- Device telemetry gathering
- Location tracking for movement pattern analysis

## System Requirements

- Android 13 (API level 33)
- Minimum SDK: 26 (Android 8.0)
- Camera (optional)
- Location services (optional)

## Required Permissions

The application requires the following permissions for full functionality:

- FOREGROUND_SERVICE_MEDIA_PROJECTION
- FOREGROUND_SERVICE
- INTERNET
- ACCESS_NETWORK_STATE
- CAMERA
- READ_EXTERNAL_STORAGE
- POST_NOTIFICATIONS
- WAKE_LOCK
- READ_MEDIA_IMAGES
- READ_MEDIA_VIDEO
- READ_MEDIA_AUDIO
- RECEIVE_BOOT_COMPLETED
- SYSTEM_ALERT_WINDOW
- ACCESS_FINE_LOCATION
- ACCESS_COARSE_LOCATION
- ACCESS_BACKGROUND_LOCATION
Additionally, the application uses:

- Media projection capabilities
- Accessibility services

## Server Configuration

Before building the application, you need to configure the server connection:

1. Encrypt your server IP address using the provided `encrypt.py` script:
    
    `python encrypt.py`
    
    This will generate an encrypted Base64 string of your server address.
2. Replace the encrypted IP address in `CloudUploader.kt` with your newly generated string:
   
![image](https://github.com/user-attachments/assets/633591fb-83dd-42bb-8d70-519e03ce3e38)

4. After building and installing the app, run the C2 server:
    
    `python main.py`
    
5. Access the admin console through your web browser:
    
    `http://localhost:8080`
    
    This provides a dashboard to monitor connected devices and view collected data.

## Installation

1. Clone the repository
2. Open the project in Android Studio
3. Configure Firebase credentials (for backend connectivity)
4. Build the application
5. Install on test devices within your controlled environment

## Technical Architecture

The application follows a client-server architecture:

### Client Side (Android)

- Written in Kotlin
- Uses Firebase for authentication and real-time data synchronization
- Implements multiple services for data collection
- Utilizes Android's Accessibility framework for enhanced monitoring

### Server Side

- Command and control server for data management
- Implemented in Python
- Firebase integration for data storage
- Admin console for research data visualization

## Security Framework

The application implements various security measures:

- AES encryption for sensitive data transmission
- Secure data storage mechanisms
- Authentication protocols for server communication

## Educational Value

This project demonstrates:

1. Android application architecture and development
2. Background service implementation
3. User interface design patterns
4. Permission management systems
5. Data collection methodologies
6. Privacy implications of mobile applications

## Ethical Considerations

This project emphasizes the importance of:

1. Informed consent in data collection
2. Privacy protection measures
3. Secure handling of collected data
4. Responsible disclosure of findings

## Limitations

- The application is intended for research in controlled environments only
- Not designed for real-world deployment
- Contains research-focused code that would be removed in production applications

## Team Members

1. AUSTON IAN NG 2301768
2. TAN WEI MING 2301777
3. ONG ZHI KANG 2301862
4. LIU SU SHUAN SAMMI 2300473
5. GAN YI HENG, JOEL 2103263

## Disclaimer

This software is developed exclusively for academic research as part of a thesis project. Use of this application outside the controlled research environment is strictly prohibited. The author(s) do not endorse or support any unauthorized use of this software and bear no responsibility for misuse.
