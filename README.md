# roesc

[![CircleCI](https://circleci.com/gh/Ingemark/roesc.svg?style=svg)](https://circleci.com/gh/Ingemark/roesc)

This is the RoomOrders (https://www.roomorders.com) escalation component which
is run periodically as AWS Lambda. It receives requests to start and stop
escalation processes from RoomOrders backend, checks for due notifications and
sends them using various protocols (e.g. makes phone calls using the Twilio
service or sends emails using SMTP protocol.)

Start requests should be sent when an order is created in the RoomOrders system
for a restaurant and stop requests should be sent when any order in the restaurant is confirmed. 

The rationale of stopping the escalation process when any order in a restaurant
is confirmed is that making phone calls is unnecessary if restaurant staff are
already reviewing the order list.

Here are some examples.

```json
{
 "process-id": "restaurantname",
 "action": "start",
 "notifications": [{"channel": "phone",  "at": 1555057084, "phone-number": "+3851111111"},
                   {"channel": "phone",  "at": 1555057096, "phone-number": "+3851111111"},
                   {"channel": "email",  "at": 1555057096, "email": "me@example.org"},
                   {"channel": "pubnub", "at": 1555057096, "pubnub-channel": "ch13234"}]
}
```

```json
{
 "process-id": "restaurantname",
 "action": "stop",
}
```

The meaning of attributes is the following:
- process-id: mandatory; an id of the escalation process, usually one escalation process per restaurantname,
- action: mandatory; "start" or "stop",
- channel: mandatory; "phone", "email", or "pubnub",
- at: (Unix epoch) time when the notification should be sent,
- phone-number: "full-format" phone number which to call when channel is "phone",
- email: destination email address when channel is "email",
- pubnub-channel: Pubnub channel for notifications when channel is "pubnub".

## Specification

Here are few key characteristics of the component.

* The component operates in two phases: first, receiving requests to initiate
  escalation processes and second, delivering scheduled notifications when their
  time comes. Notifications are delivered through different notification
  channels (such as email, phone calls etc). The phases are handled by the
  initiator and activator, respectively. Delivery of notifications is performed
  by notifiers.
* The initiator reads requests from SQS and stores notification data into the
  database.
* Requests can initiate an escalation process or cancel it.
* Requests contain a list of notifications which need to be delivered when their
  time comes. Each notification carries a timestamp of when it should be
  delivered.
* Escalation processes are uniquely identified by a process id. 
* Requests to initiate a process which already exists are ignored. Requests to
  stop an escalation process which does not exist are ignored.
* An escalation process terminates either upon a stop request or if there are no
  more scheduled notifications. (An escalation process cannot be canceled by,
  for example, answering a phone call.)
* Unrecognized/invalid requests must be logged and ignored.
* Data on currently active escalation processes are kept in the database.
* Process data contain timestamped notifications and a timestamp indicates when
  a notification should be delivered.
* The activator periodically polls the database to find notifications which
  should be delivered based on their timestamp and the current time.
* A notification is delivered by sending a request to the appropriate notifier.
* There is only "phone" notifier at this time, but it should be possible to add
  more such as "email", "console" etc.
* Notifiers should be able to process notification requests in parallel.

## Installation

Deploy as AWS Lambda or create an uberjar and run it. Check the various
environment variables in config.clj.

Must use AWS SQS FIFO queue so that ordering of the requests is preserved.

Important! Use must set MessageGroupId and MessageDeduplicationId in messages
you send as requests to SQS. You should use restaurant ID as MessageGroupId. For
MessageDeduplicationId, you can use UUID or any combination of attributes which
will not be repeated.

## Usage

### Running in AWS Lambda

This component is deployed as AWS Lambda. See instructions for running
integration tests below. When running in AWS Lambda, the same kind of setup has
to be provided.

### Running automated tests

Integration tests are tagged with keyword :integration.

* Running unit tests

```
lein test
```

* Running integration tests

Tests tagged with :integration typically connect to AWS SQS, to Postgresql
database and to the Twilio Web Service. Before running them, you should set up
the environment in the following way.

Pre-requisites:

1. An SQS FIFO queue in AWS.
2. Twilio account.
3. Postgresql database with tables created by DDL in dev-resources/initiate.sql
   script.

Running the tests:

1. Set AWS credentials in $HOME/.aws/credentials file or in any other way
   described in section "Credentials" at
   https://github.com/cognitect-labs/aws-api
2. Set REQUEST_QUEUE env. var. to the URL of the SQS queue.
3. Set TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and TWILIO_URL environment
   variables. Note that TWILIO_URL is a link to an XML file which contains
   instructions for Twilio to run on established calls. It is not a URL of the
   Twilio service!
4. Set database access environment variables DB_HOST, DB_PORT, DB_NAME, DB_USER,
   DB_PASSWORD.

Example of caller id registry configuration which must be present in the
database table `property` with `property_key` being `caller-id-registry`':

```
{
    "+385": "+3851111111",
    "+61": "+6117065677",
    "default": "+12569065677"
}
```

This configuration means that calls to numbers starting with +385 will have
+3851111111 set as caller id, etc.

Example of a configuration XML pointed to by TWILIO_URL environment variable:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Response>
    <Say>Hello! You have new orders in the Roomorders service.</Say>
    <Pause length="1"/>
    <Hangup/>
</Response>
```


## Options

Environment variables used by the application are defined in
src/roesc/config.clj file. (No other file should reference environment variables
directly so config.clj is the only place to check.)

## License

Copyright © 2019 Josip Gracin

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
