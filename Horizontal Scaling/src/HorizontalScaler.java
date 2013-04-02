import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.codec.binary.Base64;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest;
import com.amazonaws.services.autoscaling.model.DeleteAutoScalingGroupRequest;
import com.amazonaws.services.autoscaling.model.PutNotificationConfigurationRequest;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult;
import com.amazonaws.services.autoscaling.model.UpdateAutoScalingGroupRequest;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.MetricDatum;
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;
import com.amazonaws.services.cloudwatch.model.PutMetricDataRequest;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.cloudwatch.model.StatisticSet;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerPolicyRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.Listener;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest;

public class HorizontalScaler {
	static Properties properties;
	static BasicAWSCredentials bawsc;
	static AmazonEC2Client ec2;
	static AmazonCloudWatchClient cw;
	static AmazonAutoScalingClient autoscaler;
	static AmazonElasticLoadBalancingClient elb;
	public static void main(String[] args) throws IOException {
		// setup properties
		System.out.println("Setting up properties and clients");
		properties = new Properties();
		properties.load(HorizontalScaler.class.getResourceAsStream("/AwsCredentials.properties"));
		
		bawsc = new BasicAWSCredentials(properties.getProperty("accessKey"), properties.getProperty("secretKey"));
		
		//Launch an EC2 and AmazonCloud Client
		ec2 = new AmazonEC2Client(bawsc);
		cw = new AmazonCloudWatchClient(bawsc);
		autoscaler = new AmazonAutoScalingClient(bawsc);
		elb = new AmazonElasticLoadBalancingClient(bawsc);
		//createHorizontalScaler();	
		deleteAutoScalingGroup("nbatliva-horizontalscaler2");
	}
	
	public static void deleteAutoScalingGroup(String name) {
		UpdateAutoScalingGroupRequest ureq = new UpdateAutoScalingGroupRequest();
		ureq.withAutoScalingGroupName(name);
		ureq.withMaxSize(0).withMinSize(0);
		autoscaler.updateAutoScalingGroup(ureq);
		DeleteAutoScalingGroupRequest req = new DeleteAutoScalingGroupRequest();
		req.withAutoScalingGroupName(name);
		autoscaler.deleteAutoScalingGroup(req);
	}
	
	public static void createHorizontalScaler() {
		System.out.println("Creating elastic load balancer");
		CreateLoadBalancerRequest createreq = new CreateLoadBalancerRequest();
		createreq
			.withLoadBalancerName("horizontalscaler2")
			.withAvailabilityZones("us-east-1c");
		Listener listener = new Listener();
		listener
			.withLoadBalancerPort(3306)
			.withInstancePort(3306)
			.withProtocol("TCP");
		createreq.withListeners(listener);
		try {
			elb.createLoadBalancer(createreq);
		} catch (Exception e) {
			System.out.println(e);
			return;
		}
		System.out.println("Creating custom metric user data");
		StringBuilder userData = new StringBuilder();
		userData.append("#!/bin/bash");
		userData.append("");
		
		System.out.println("Creating launch configuration");
		CreateLaunchConfigurationRequest launchrequest = new CreateLaunchConfigurationRequest();
		launchrequest
			.withLaunchConfigurationName("nbatliva-horizontalscaler-launch2")
			.withImageId("ami-fab52b93")
			.withInstanceType("m1.small")
			.withSecurityGroups("default")
			.withKeyName("awskeypair")
			.withUserData(Base64.encodeBase64String(userData.toString().getBytes()));
		try {
			autoscaler.createLaunchConfiguration(launchrequest);
		} catch (Exception e) {
			// check if error appeared because launch config
			// already exists
			System.out.println(e.toString().substring(e.toString().indexOf("AWS Error Message")));
			if (!e.toString().contains("AlreadyExists")) {
				return;
			}
		}
		
		// Create Auto Scaling Group
		System.out.println("Creating auto scaling group");
		CreateAutoScalingGroupRequest groupreq = new CreateAutoScalingGroupRequest();
		groupreq
			.withAutoScalingGroupName("nbatliva-horizontalscaler2")
			.withMinSize(1)
			.withMaxSize(4)
			.withDesiredCapacity(1)
			.withLaunchConfigurationName("nbatliva-horizontalscaler-launch2")
			.withAvailabilityZones("us-east-1c")
			.withLoadBalancerNames("horizontalscaler2");
		try {
			autoscaler.createAutoScalingGroup(groupreq);
		} catch (Exception e) {
			// check if error appeared because scaling group
			// already exists
			if (!e.toString().contains("AlreadyExists")) {
				return;
			}
		}
		
		System.out.println("Creating scale out policy");
		PutScalingPolicyRequest policyrequest = new PutScalingPolicyRequest();
		policyrequest.setPolicyName("ScaleOut");
		policyrequest.setScalingAdjustment(1);
		policyrequest.setAdjustmentType("ChangeInCapacity");
		policyrequest.setAutoScalingGroupName("nbatliva-horizontalscaler2");
		PutScalingPolicyResult policyresult = autoscaler.putScalingPolicy(policyrequest);
		String ARN = policyresult.getPolicyARN();
		PutMetricAlarmRequest alarmRequest = new PutMetricAlarmRequest();
		alarmRequest.setAlarmName("ScaleOutAlarm");
		alarmRequest.setAlarmActions(Arrays.asList(ARN));
		alarmRequest.setMetricName("TPSUtilization");
		alarmRequest.setNamespace("HorizontalScalingSpace");
		alarmRequest.setStatistic("Average");
		alarmRequest.setThreshold(75.0);
		alarmRequest.setPeriod(5 * 60);
		alarmRequest.setComparisonOperator("GreaterThanThreshold");
		alarmRequest.setEvaluationPeriods(1);
		cw.putMetricAlarm(alarmRequest);
		
		System.out.println("Creating alarm for scale in policy");
		policyrequest = new PutScalingPolicyRequest();
		policyrequest.setPolicyName("ScaleIn");
		policyrequest.setScalingAdjustment(-1);
		policyrequest.setAdjustmentType("ChangeInCapacity");
		policyrequest.setAutoScalingGroupName("nbatliva-horizontalscaler2");
		policyresult = autoscaler.putScalingPolicy(policyrequest);
		ARN = policyresult.getPolicyARN();
		alarmRequest = new PutMetricAlarmRequest();
		alarmRequest.setAlarmName("ScaleInAlarm");
		alarmRequest.setAlarmActions(Arrays.asList(ARN));
		alarmRequest.setMetricName("TPSUtilization");
		alarmRequest.setNamespace("HorizontalScalingSpace");
		alarmRequest.setStatistic("Average");
		alarmRequest.setThreshold(25.0);
		alarmRequest.setPeriod(5 * 60);
		alarmRequest.setComparisonOperator("LessThanThreshold");
		alarmRequest.setEvaluationPeriods(1);
		cw.putMetricAlarm(alarmRequest);
		
		// set up email notification
		PutNotificationConfigurationRequest emailRequest = new PutNotificationConfigurationRequest();
		emailRequest.setAutoScalingGroupName("nbatliva-horizontalscaler2");
		emailRequest.setTopicARN("arn:aws:sns:us-east-1:556704617897:Project2");
		String[] types = new String[2];
		types[0] = "autoscaling:EC2_INSTANCE_LAUNCH";
		types[1] = "autoscaling:EC2_INSTANCE_TERMINATE";
		emailRequest.withNotificationTypes(types);
		autoscaler.putNotificationConfiguration(emailRequest);
	}
}
