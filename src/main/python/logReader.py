import json
import glob
import csv
def get_metrics(path):
    exclude_key = ['Updated Blocks','Host Name','Input Metrics']
    time_key = ['Executor Deserialize Time','Result Serialization Time','Executor Run Time']
    ans = dict()
    name = "crazydog"
    ans['Scheduler Delay Time'] = 0
    ans['Getting Result Time'] = 0
    with open(path) as f:
        for line in f:
            data = json.loads(line)
            if(data['Event']=='SparkListenerTaskEnd'):
                schedulerDelayTime = data['Task Info']['Finish Time']-data['Task Info']['Launch Time']-data['Task Info']['Getting Result Time']
                ans['Getting Result Time'] += data['Task Info']['Getting Result Time']
                metrics = data['Task Metrics']
                for key in metrics:
                    if(key in exclude_key):
                        continue
                    if(key in time_key):
                        schedulerDelayTime -= metrics[key]
                    if(type(metrics[key])==dict):
                        for subkey in metrics[key]:
                            if subkey in ans:
                                ans[subkey] += metrics[key][subkey]
                            else:
                                ans[subkey] = metrics[key][subkey]
                    elif key in ans:
                        ans[key] += metrics[key]
                    else:
                        ans[key] = metrics[key]
                ans['Scheduler Delay Time'] += schedulerDelayTime
            elif(data['Event']=='SparkListenerApplicationStart'):
                application_time = data['Timestamp']
            elif(data['Event']=='SparkListenerApplicationEnd'):
                application_time = data['Timestamp']-application_time
            elif(data['Event']=='SparkListenerEnvironmentUpdate'):
                name = data['Spark Properties']['spark.app.name']
    ans['Shuffle Write Time'] *= 1e-6
    ans['Transforming Time'] = ans['Result Serialization Time']+ans['Executor Deserialize Time']+ans['Getting Result Time']
    ans['Executor Computing Time'] = ans['Executor Run Time']-ans['Shuffle Write Time']-ans['Fetch Wait Time']
    # for key in ans:
    #         ans[key] /= 4
    ans['Application Time'] = application_time
    ans['query id'] = path
    ans['name'] = name
    return ans

list_of_files = glob.glob('D://canrangou/app-*')
print(list_of_files)
hasheader = 0
with open("result.csv", "w") as f:
    for fileName in list_of_files:
        ans = get_metrics(fileName)
        w = csv.DictWriter(f,ans.keys())
        if hasheader==0 :
            w.writeheader()
            hasheader = 1
        w.writerow(ans)
        print("------------------------------------------------------")
        print(ans['name'])
        for key in ans:
            print(key," ",ans[key])
f.close()