import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

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
import com.amazonaws.services.cloudwatch.model.PutMetricAlarmRequest;
import com.amazonaws.services.ec2.AmazonEC2Client;

public class Main {
	static Properties properties;
	static BasicAWSCredentials bawsc;
	static AmazonEC2Client ec2;
	static AmazonCloudWatchClient cw;
	static AmazonAutoScalingClient autoscaler;

	public static void main(String[] args) throws IOException, InterruptedException {
		// setup properties
		System.out.println("Setting up properties and clients");
		properties = new Properties();
		properties.load(Main.class.getResourceAsStream("/AwsCredentials.properties"));

		bawsc = new BasicAWSCredentials(properties.getProperty("accessKey"), properties.getProperty("secretKey"));

		//Launch an EC2 and AmazonCloud Client
		ec2 = new AmazonEC2Client(bawsc);
		cw = new AmazonCloudWatchClient(bawsc);
		autoscaler = new AmazonAutoScalingClient(bawsc);
		//part1();
		//part2();
		//part3();
		DeleteAutoScalingGroupRequest delete = new DeleteAutoScalingGroupRequest();
		delete.withAutoScalingGroupName("username-project2");
		autoscaler.deleteAutoScalingGroup(delete);
	}

	public static void part3() {
		System.out.println("Creating launch configuration");
		CreateLaunchConfigurationRequest launchrequest = new CreateLaunchConfigurationRequest();
		launchrequest
			.withLaunchConfigurationName("username-project2-launch")
			.withImageId("ami-92b82afb")
			.withInstanceType("m1.small")
			.withSecurityGroups("default")
			.withKeyName("awskeypair");
		try {
			autoscaler.createLaunchConfiguration(launchrequest);
		} catch (Exception e) {
			// check if error appeared because launch config
			// already exists
			if (!e.toString().contains("AlreadyExists")) {
				return;
			}
		}

		// Create Auto Scaling Group
		System.out.println("Creating auto scaling group");
		CreateAutoScalingGroupRequest groupreq = new CreateAutoScalingGroupRequest();
		groupreq
			.withAutoScalingGroupName("username-project2")
			.withMinSize(1)
			.withMaxSize(10)
			.withDesiredCapacity(4)
			.withLaunchConfigurationName("username-project2-launch")
			.withAvailabilityZones("us-east-1b");

		try {
			autoscaler.createAutoScalingGroup(groupreq);
		} catch (Exception e) {
			// check if error appeared because scaling group
			// already exists
			if (!e.toString().contains("AlreadyExists")) {
				return;
			}
		}

		// scaling out policy and alarm
		System.out.println("Creating scale out policy");
		PutScalingPolicyRequest policyrequest = new PutScalingPolicyRequest();
		policyrequest.setPolicyName("ScaleOut");
		policyrequest.setScalingAdjustment(1);
		policyrequest.setAdjustmentType("ChangeInCapacity");
		policyrequest.setAutoScalingGroupName("username-project2");
		PutScalingPolicyResult policyresult = autoscaler.putScalingPolicy(policyrequest);

		System.out.println("Creating alarm for scale out policy");
		String ARN = policyresult.getPolicyARN();
		PutMetricAlarmRequest alarmRequest = new PutMetricAlarmRequest();
		alarmRequest.setAlarmName("ScaleOutAlarm");
		alarmRequest.setAlarmActions(Arrays.asList(ARN));
		alarmRequest.setMetricName("CPUUtilization");
		alarmRequest.setNamespace("AWS/EC2");
		alarmRequest.setStatistic("Average");
		alarmRequest.setThreshold(75.0);
		alarmRequest.setPeriod(5 * 60);
		alarmRequest.setComparisonOperator("GreaterThanThreshold");
		alarmRequest.setEvaluationPeriods(1);
		cw.putMetricAlarm(alarmRequest);

		// scaling in policy and alarm
		System.out.println("Creating scale in policy");
		policyrequest = new PutScalingPolicyRequest();
		policyrequest.setPolicyName("ScaleIn");
		policyrequest.setScalingAdjustment(-1);
		policyrequest.setAdjustmentType("ChangeInCapacity");
		policyrequest.setAutoScalingGroupName("username-project2");
		policyresult = autoscaler.putScalingPolicy(policyrequest);

		System.out.println("Creating alarm for scale in policy");
		ARN = policyresult.getPolicyARN();
		alarmRequest = new PutMetricAlarmRequest();
		alarmRequest.setAlarmName("ScaleInAlarm");
		alarmRequest.setAlarmActions(Arrays.asList(ARN));
		alarmRequest.setMetricName("CPUUtilization");
		alarmRequest.setNamespace("AWS/EC2");
		alarmRequest.setStatistic("Average");
		alarmRequest.setThreshold(25.0);
		alarmRequest.setPeriod(5 * 60);
		alarmRequest.setComparisonOperator("LessThanThreshold");
		alarmRequest.setEvaluationPeriods(1);
		cw.putMetricAlarm(alarmRequest);

		// set up email notification
		PutNotificationConfigurationRequest emailRequest = new PutNotificationConfigurationRequest();
		emailRequest.setAutoScalingGroupName("username-project2");
		emailRequest.setTopicARN("arn:aws:sns:us-east-1:556704617897:Project2");
		String[] types = new String[2];
		types[0] = "autoscaling:EC2_INSTANCE_LAUNCH";
		types[1] = "autoscaling:EC2_INSTANCE_TERMINATE";
		emailRequest.withNotificationTypes(types);
		autoscaler.putNotificationConfiguration(emailRequest);

		UpdateAutoScalingGroupRequest update = new UpdateAutoScalingGroupRequest();
		update.withAutoScalingGroupName("username-project2").withMaxSize(0).withMinSize(0);
		autoscaler.updateAutoScalingGroup(update);

		DeleteAutoScalingGroupRequest delete = new DeleteAutoScalingGroupRequest();
		delete.withAutoScalingGroupName("username-project2");
		autoscaler.deleteAutoScalingGroup(delete);
	}

	/*
	// launch 1 instance and monitor for 30 minutes
	public static void part1() throws IOException, InterruptedException {

		String instanceId = createInstance();

		// Conigure CPUUtilization request
		GetMetricStatisticsRequest statreq = new GetMetricStatisticsRequest();
		statreq.setPeriod(60);
		statreq.setMetricName("CPUUtilization");
		statreq.setNamespace("AWS/EC2");
		statreq.setStatistics(Arrays.asList("Average"));

		// Send 6 requests and print to standard out
		Calendar now;
		for (int i = 0; i < 6; i++) {
			Thread.sleep(5 * 60 * 1000);
			now = Calendar.getInstance();
			statreq.setEndTime(now.getTime());
			now.add(Calendar.MINUTE, -5);
			statreq.setStartTime(now.getTime());
			GetMetricStatisticsResult statres = cw.getMetricStatistics(statreq);
			double avg = statres.getDatapoints().get(0).getAverage();
			System.out.println(
				"CPUUtilization from " +
				statreq.getStartTime() +
				" to " +
				statreq.getEndTime() +
				": " + avg + "%"
			);
		}

		// terminate instance
		TerminateInstancesRequest terminatereq = new TerminateInstancesRequest(Arrays.asList(instanceId));
		ec2.terminateInstances(terminatereq);
	}

	// launch new instances if CPUUtilization > 75%
	public static void part2() throws IOException, InterruptedException {

		ArrayList<String> instanceIds = new ArrayList<String>();
		instanceIds.add(createInstance());

		// Conigure CPUUtilization request
		GetMetricStatisticsRequest statreq = new GetMetricStatisticsRequest();
		statreq.setPeriod(60);
		statreq.setMetricName("CPUUtilization");
		statreq.setNamespace("AWS/EC2");
		statreq.setStatistics(Arrays.asList("Average"));

		// Send 6 requests and print to standard out
		Calendar now;
		for (int i = 0; i < 6; i++) {
			Thread.sleep(5 * 60 * 1000);
			now = Calendar.getInstance();
			statreq.setEndTime(now.getTime());
			now.add(Calendar.MINUTE, -5);
			statreq.setStartTime(now.getTime());
			GetMetricStatisticsResult statres = cw.getMetricStatistics(statreq);
			double avg = statres.getDatapoints().get(0).getAverage();
			System.out.println(
					"CPUUtilization from " +
					statreq.getStartTime() +
					" to " +
					statreq.getEndTime() +
					": " + avg + "%"
			);
			if (avg > 75) {
				instanceIds.add(createInstance());
			}
		}

		// terminate instance
		TerminateInstancesRequest terminatereq = new TerminateInstancesRequest(instanceIds);
		ec2.terminateInstances(terminatereq);
	}

	// create a new instance, returns the instance id
	public static String createInstance(String ami, String type) throws InterruptedException {
		System.out.println("Creating new instance");
		//Create Instance Request
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

		//Configure Instance Request
		runInstancesRequest.withImageId(ami)
		.withInstanceType(type)
		.withMinCount(1)
		.withMaxCount(1)
		.withKeyName("awskeypair")
		.withSecurityGroups("default");

		//Launch Instance
		RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);

		//Return the Object Reference of the Instance just Launched
		Instance instance = runInstancesResult.getReservation().getInstances().get(0);

		//Monitor Instance
		MonitorInstancesRequest monitorInstancesRequest = new MonitorInstancesRequest(Arrays.asList(instance.getInstanceId()));
		ec2.monitorInstances(monitorInstancesRequest);

		waitForInstanceRunning(instance);

		System.out.println("New Instance ID: " + instance.getInstanceId());
		return instance.getInstanceId();
	}

	// equivalent to waiting 5 minutes until instance is running
	public static void waitForInstanceRunning(Instance instance) throws InterruptedException {
		boolean running = false;
		while (!running) {
			DescribeInstancesRequest di = new DescribeInstancesRequest();
			di.withInstanceIds(Arrays.asList(instance.getInstanceId()));
			DescribeInstancesResult result = ec2.describeInstances(di);
			Instance i = result.getReservations().get(0).getInstances().get(0);
			if (i.getState().getName().equals("running")) {
				running = true;
			} else {
				// sleep for 2 minutes
				Thread.sleep(2*60*1000);
			}
		}
	}
	*/
}
