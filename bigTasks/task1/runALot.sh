
n=$1

for((i=0; i < n; i++))
do
    java cp2023.demo.TransferBurst > /dev/null
    echo $?
done