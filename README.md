# gateway-service

ADD LOGGING OF INCOMING REQUEST AND OUTGOING RESPONSE LIKE IN OLDER GATEWAYS

add endpoints for ping/test, ping/reset, reset routes map, set log level (can we add timer to run once and then not run again)

in the GatewayRequestHandler, add a check for gatewaysvc, and route those requests to somewhere else to process all above
