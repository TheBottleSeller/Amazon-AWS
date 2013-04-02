#!/bin/bash
sudo -s <<EOF
cd /var/lib/mysql
service mysql stop
cp -a /home/mysql_backup/* .
service mysql start
cd ~
apt-get install wget
wget http://ec2-downloads.s3.amazonaws.com/CloudWatch-2010-08-01.zip
mkdir CloudWatch
unzip CloudWatch-2010-08-01.zip -d CloudWatch
EOF
cd ~
echo "show status like 'Queries'; show status like 'Uptime';" > metric.sql
echo '#!/bin/bash' > metric.sh
echo 'mysql < "metric.sql" > results.txt' >> metric.sh
echo 'TRANS=$(grep -o "[0-9]*" results.txt | head -1)' >> metric.sh
echo 'TRANS=$(echo "scale=1; $TRANS -6" | bc)' >> metric.sh
echo 'UPTIME=$(grep -o '[0-9]*' results.txt | tail -1)' >> metric.sh
echo 'TPERQ=16' >> metric.sh
echo 'MAXTPS=143.2' >> metric.sh
echo 'TPSUTIL=$(echo "scale=5; $TRANS / $UPTIME / $TPERQ / $MAXTPS" | bc)' >> metric.sh
echo 'export AWS_CLOUDWATCH_HOME=~/CloudWatch/CloudWatch-1.0.13.4' >> metric.sh
echo '$AWS_CLOUDWATCH_HOME/bin/mon-put-data --metric-name TPSUtilization --namespace HorizontalScalingSpace --value $TPSUTIL --unit Percent --I AKIAIIY2LIYSTBLCCOBA --S QtvnwIA/1/zxyBhHHz+lB8U7eeuDnN7HWsKsZ6D+' >> metric.sh
sudo chmod +x metric.sh
echo "*/1 * * * * /home/ubuntu/metric.sh" > interval.txt
sudo crontab interval.txt
