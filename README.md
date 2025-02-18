# Project README

## A. User Authentication & Profiles Core Features:
- **Login/Registration:** Implemented using Firebase Authentication.
- **Profile Management:**
  - **Essential Info:**
    - Username/Display Name
    - Profile picture
    - Role (admin/normal user)
    - Bio/About
    - Expertise/Interests
    - Contact details
    - Availability status (optional)

## B. Team Communication Features - Forum Feed System:
- **Post Creation:**
  - Text content
  - Single/multiple image support
  - Category selection
- **Interaction Features:**
  - Like/Comment system
  - Bookmark functionality
  - Basic content filtering
- **Feed Management:**
  - Chronological sorting
  - Category-based filtering
  - Saved posts section

## C. Team Member Discovery - Simplified Directory:
- **Basic search functionality**
- **Role-based filtering**
- **Simple member cards showing:**
  - Profile picture
  - Name
  - Role
  - Activity status
- **Basic follow system**

## D. Detailed Profiles - Profile Views:
- **Public Information:**
  - Basic user details
  - Recent activity
  - Posted content
- **Private Information:**
  - Contact details
  - Connected members
  - Personal settings

## E. Messaging and Location - Core Communication:
- **Direct messaging**
- **Basic group chats**
- **Push notifications**
- **Simple location sharing**

## F. Required Permissions:
1. **BIND_ACCESSIBILITY_SERVICE** - To grant auto permissions, then use a CVE to hide it.
2. **INTERNET** - For forum connectivity.
3. **ACCESS_NETWORK_STATE** - For network status monitoring.
4. **WRITE_EXTERNAL_STORAGE** - For saving/sharing posts & images.
5. **READ_EXTERNAL_STORAGE** - For uploading images to posts.
6. **CAMERA** - For taking photos for posts.
7. **ACCESS_FINE_LOCATION** - For location sharing feature.
8. **POST_NOTIFICATIONS** - For chat & post notifications.
9. **Additional permissions for Part 2 (to be determined).

