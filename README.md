## Synopsis

This project separates the concern of handling a payment gateway from the client requiring the integration.

Benefits include ease of financial transaction reporting, payment event and type handling.
 
Furthermore, although WorldPay is the only current payment gateway implemented the abstraction makes implementing alternative providers easier from the client perspective. 

## Code Example

To learn the features implemented in this project the suggested starting place is the controller tests you can find [here](payments/test/controller/), specifically PaymentControllerSpec, PaymentEventControllerSpec and WorldPayTransactionReportControllerSpec.

## Motivation

This project exists because it is useful for the Home Office Visa exemplar and payment gateway integration is common to many government projects.

## Installation

This project is implemented in Scala and uses Mongo for data persistence.

To build the project execute `./build.sh`, and likewise to run execute `./run.sh` from the project root folder.

## API Reference

This project is a web application exposing RESTful web services. The expected content-type is `application/json` except from endpoints specific for WorldPay.

### `GET /payment-types/:region`

#### Description

Returns a list of payment types for a given `region`.

#### Header attributes

`X-CLIENT-ID` - to define client id

#### Parameters

* `region` - a `string` parameter for a region name; it has to match one of regions specified in the configuration.

#### Response

A json representing set of payment types.


### `POST /payment/start`

#### Description

Starts a new payment by initializing it on both Payments and the WorldPay side and returns a response json with a redirect `url`.

#### Header attributes

`X-CLIENT-ID` - to define client id

#### Payload

A json structure containing all required data.

Example:

```
{
  "externalReference": "pnn",
  "internalReference": "appId",
  "payee": {
    "title": "Mr",
    "givenName": "givenName",
    "familyName": "familyName",
    "email": "lincolnshire.poacher@example.com",
    "phoneNumber": "0123456789",
    "billingAddress": {
      "line1": "line1",
      "line2": "line2",
      "line3": "line3",
      "townCity": "London",
      "postCode": "EC2 2CE",
      "countryCode": "CHN"
    }
  },
  "description": "I am a product",
  "profile": {
    "paymentType": "VISA-SSL",
    "region": "uk"
  },
  "paymentItems": [{
     "description": "6 months",
     "price": 234.0
  }
  ],
  "total": 234.0,
  "currency": "GBP",
  "locale": "",
  "additionalInformation": {
      "GWF": "123"
   }
}
```

#### Response

A json containing `url` to be used to redirect to payment pages along with payment `externalReference`.


### `GET /payment/perform-inquiry/:internalReference`

#### Description

Forces to perform inquiry on the WorldPay to check the current status of the latest payment for the given `internalReference`.

As a result the service may asynchronously send a notification to the client system informing about the change in payment status.

#### Parameters

* `internalReference` - a `string` parameter for internal payment reference.

#### Response

No specific response body.


### `GET /payment-submission/confirmation`

#### Description

An endpoint to handle synchronous WorldPay callbacks on payment submission. There is number of query parameters WorldPay adds to the query, however the service takes into account only `status` and `externalReference`.

This main purpose of this endpoint it to handle *APM* payments as the `status` query parameter is added only for them.

As a result the service may asynchronously send a notification to the client system informing about the change in payment status.

#### Parameters

* `externalReference` - a `string` parameter for external payment reference;
* `status` - a `string` representing current payment status; there is a set of possible values for that field.

#### Response

Response body required by WorldPay: `[OK]`


### `GET /notify`

#### Description

An endpoint to receive asynchronous WorldPay notifications about a payment. There is number of query parameters WorldPay adds to the query to both identify the payment and to give information about its current status.

As a result the service may asynchronously send a notification to the client system informing about the change in payment status.

#### Parameters

* `PaymentCurrency` - a `string` parameter for currency used on the payment;
* `PaymentStatus` - a `string` representing current payment status;
* `OrderCode` - a `string` parameter for the payment external reference;
* `PaymentMethod` - a `string` parameter for payment method used on the payment;
* `PaymentAmount` - a `number` parameter with the paid amount; value multiplied by 100; contains no commas.

#### Response

Response body required by WorldPay: `[OK]`


### `POST /report`

#### Description

An endpoint to receive reports from WorldPay. It generates two reports and sends them as email attachments to recipients specified in the config file.

The endpoint payload is in XML format.

#### Response

Response body required by WorldPay: `<html> <head>Report Response</head> <body> [OK] </body> </html>`


### `POST /migration/payment`

#### Description

This endpoint creates initial payment data on the service side. It does not initializes payment on WorldPay side, though.

The endpoint is for a parallel phase when a client system was already integrated with WorldPay and is moving to use the new gateway system.

#### Header attributes

`X-CLIENT-ID` - to define client id

#### Response

No specific response body.


### `GET /healthcheck`

#### Description

An endpoint to check service's health.

#### Response

Returns `healthy!` along with build number if everything is fine.


### Callback urls

The service requires defining several urls in order to interact with client systems.

#### `payment.report.url`

A client url where generated reports are sent.

#### `payment.notification.url`

A client url where notifications about payment status change are sent.

#### `pending.url`

A client url where payment journey gets redirected for pending payment.

#### `cancel.url`

A client url where payment journey gets redirected in case of payment being cancelled.


## Tests

All tests are executed in the build process.

However if you are learning how the service works through running individual tests, this is best done in an IDE such as IntelliJ.

## Contributors

If you want to contribute to the project you can do it by creating a pull request.


## Known issues

There is no validation of the string fields within the JSON payload which, if not correctly protected by the calling client, could leave the potential for a XML injection attack.

