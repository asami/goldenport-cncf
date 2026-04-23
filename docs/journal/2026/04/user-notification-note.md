# UserNotification

=================================
# Overview

UserNotification is a stateful component that manages notifications for users.

It represents:
- what should be communicated to the user
- how it is tracked over time (read/unread, history)
- how it is presented via application interfaces

UserNotification does NOT handle message delivery.


=================================
# Concept

UserNotification models:

- user-facing information
- system-generated announcements
- alerts and updates
- workflow-related notifications

It is:

- user-centric
- stateful
- persistent


=================================
# Responsibilities

- Create notifications for users
- Persist notification data
- Manage read/unread status
- Provide notification history
- Control visibility duration (e.g., expiration)


=================================
# Non-Responsibilities

- External delivery (email, SMS, push, etc.)
- Channel selection
- Delivery driver selection
- Authentication logic


=================================
# Core Model

UserNotification
- id
- user_id
- title
- message
- type
- status (unread / read)
- created_at
- read_at
- expires_at
- link (optional)


=================================
# Behavior

- Notifications are created based on application events
- Users can view, read, and manage them
- Notifications may expire after a defined period


=================================
# Relation to Other Components

UserNotification may trigger message delivery, but does not perform it.

UserNotification
    ↓ (optional transformation)
UnifiedMessage
    ↓
MessageDelivery


=================================
# Key Design Principle

UserNotification defines:

    "what to communicate to the user"

It does NOT define:

    "how to deliver it"


=================================
# Summary

UserNotification is the domain model for managing user-visible notifications,
including state, history, and lifecycle.

It is independent from delivery mechanisms.
