Amazon-AWS
==========

Sample Code for Tasks using Amazon AWS

Note: This code will not work! It is meant to be a sample of what the code should look like to give you an idea of how to accomplish tasks using Amazon AWS Java SDK.

HorizontalScaler
The HorizontalScaler launches an auto scaling group that has scale in and scale out policies which are configured to scale based on a custom metric TPSUtilization. It scales in (reduces group capacity by 1) when TPSUtilization < 25% and scales up (increase group capacity by 1) when TPSUtilization > 75%. The code is meant to include a UserData script (it does not do this!). But the UserData script (that should be included in the launch configuration) is in the HorizontalScaler folder called UserData.sh