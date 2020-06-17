import pycurl
import json
from random import seed
from random import randint
from io import BytesIO



seed(1)
NB_QUERY = 400



## (TODO) create list of inputs to export and save it more easily

def getArgs ():
    isLeft = randint(0,1)
    if (isLeft == 1):    
        return randint(1, 10)
    else :
        return randint(30, 50)

for i in range(0, NB_QUERY):
    url = 'http://localhost:80?args={}'.format(getArgs())
    print ('calling url: {}'.format(url))
    
    c = pycurl.Curl()
    c.setopt(c.URL, url)
    c.setopt(c.HTTPHEADER, ['objective: 10000'])
    c.perform()
    c.close()

