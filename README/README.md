## Installation

1. Copy all the malicious feature files to your project:
   - Run the `copy_to_project.bat` script (Windows) or `copy_to_project.sh` (Linux/Mac)
   - This will copy all necessary files to your actual project directory

2. Configure C2 server connection:
   - Update the `C2Config.kt` file with your server IP address and port
   - For emulator testing, use 10.0.2.2:8443 which points to the host machine
   - For physical device testing, use your computer's actual IP address

3. Manually update your AndroidManifest.xml:
   - Follow the instructions in `MANIFEST_CHANGES.md`

4. Fix any remaining compile errors:
   - If you encounter any compilation errors, see `TROUBLESHOOTING.md` for solutions
   - Most common issues are related to resource references (R) and BuildConfig

# ChatterBox - Mobile Security Educational App

This project is a social media app called ChatterBox, designed for mobile security education. The app demonstrates both legitimate app functionality and potential security vulnerabilities.

## Educational Purpose

This app has been designed as a teaching tool to help students understand:

1. How mobile applications work
2. Potential security vulnerabilities in mobile apps
3. How malicious functionality can be hidden within legitimate apps
4. The importance of proper permission management
5. Techniques used for mobile security analysis and reverse engineering

## Application Structure

The application is a Reddit-like social media platform with the following features:

- User authentication (login/registration)
- Forum browsing and posting
- Private messaging
- User profiles
- Content categorization and search

## Running the Application

This application should be run **ONLY** in a controlled environment, such as:

1. An emulator (Genymotion or Android Studio Emulator)
2. A dedicated test device not used for personal information

**NEVER** install this application on your personal device or distribute it through app stores.

## Security Features for Education

This app includes a package called `malicious` that implements various security-relevant features for educational purposes:

- Keylogging demonstration using Accessibility Services
- Camera and microphone access demonstration
- Location tracking functionality
- Credential harvesting techniques
- Data collection and exfiltration simulations

These features are clearly marked with comments indicating their educational purpose and should be studied only in the context of understanding security vulnerabilities.

## Permissions

The application requests various permissions to demonstrate how they can be used (both legitimately and potentially maliciously):

- Internet and network access
- Camera and microphone
- Location services
- Storage access
- Accessibility services

## Analyzing the App

Students can use the following tools to analyze this application:

1. **Burp Suite**: For network traffic analysis
2. **Frida**: For runtime manipulation and analysis
3. **JADX/APKTool**: For static code analysis and decompilation
4. **MobSF**: For automated security analysis

## Security Warning

The code in this repository is for **EDUCATIONAL PURPOSES ONLY**. The techniques demonstrated should only be used ethically and legally, such as:

1. Analyzing your own applications for security vulnerabilities
2. Participating in legitimate bug bounty programs with proper authorization
3. Learning about mobile application security in an academic context

Unauthorized use of these techniques against others' applications is illegal and unethical.

## Project Structure

- `app/src/main/java/com/example/ChatterBox/`: Core application functionality
- `app/src/main/java/com/example/ChatterBox/malicious/`: Educational security demonstration code
- `app/src/main/res/`: Resources and layouts
- `app/src/main/AndroidManifest.xml`: Application permissions and component declarations

For detailed information about the educational security features, see `MALICIOUS_FEATURES.md`.
