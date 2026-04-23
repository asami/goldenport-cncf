# MessageDelivery and UnifiedMessage

=================================
# Overview

MessageDelivery is a component responsible for delivering messages
to external communication channels.

It operates on a channel-agnostic message representation called UnifiedMessage.


=================================
# UnifiedMessage

## Concept

UnifiedMessage is a unified, channel-independent representation of a message.

It abstracts away differences between:

- email
- SMS
- push notifications
- chat systems
- in-app notifications

## Role

UnifiedMessage serves as:

- a common input to MessageDelivery
- a shared message format across the system

## Core Structure

UnifiedMessage
- id
- user_id
- title
- body
- metadata
- created_at

(optional)
- channels (hint)


=================================
# MessageDelivery

## Concept

MessageDelivery is a delivery engine that:

- receives a UnifiedMessage
- determines delivery behavior based on context
- sends the message through one or more channels


=================================
# Responsibilities

- Accept UnifiedMessage as input
- Refer to delivery context (ExecutionContext)
- Select one or more delivery drivers
- Deliver messages to selected channels


=================================
# Delivery Context

Delivery behavior is influenced by:

- application-level configuration
- user-level preferences
- execution context defaults

This context is provided via ExecutionContext.


=================================
# Behavior

MessageDelivery performs:

1. Receive UnifiedMessage
2. Refer to ExecutionContext for delivery context
3. Determine applicable channels
4. Select corresponding drivers
5. Deliver message via one or more channels


=================================
# Multi-Channel Delivery

- Delivery is not limited to a single channel
- Multiple channels (e.g., email + SMS) may be used simultaneously
- Channel selection depends on delivery context


=================================
# Non-Responsibilities

- Managing notification state
- Storing message history
- Read/unread tracking
- UI rendering


=================================
# Relation to Other Components

MessageDelivery is typically used by:

- UserNotification
- Authentication components
- Other application services


=================================
# Key Design Principle

MessageDelivery defines:

    "how to deliver messages"

UnifiedMessage defines:

    "a unified representation of messages"


=================================
# Summary

MessageDelivery is a context-driven, multi-channel delivery mechanism.

UnifiedMessage provides a unified abstraction that enables consistent
delivery across multiple communication channels.
