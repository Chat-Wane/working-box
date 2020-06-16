import pycurl
from random import seed
from random import randint



seed(1)

def getArgs ():
    isLeft = randint(0,1)
    if (isLeft == 1):    
        return randint(10, 50)
    else :
        return randint(150, 200)


for i in range(0, 10):
    url = 'http://localhost:8080?args=10,{}'.format(getArgs())
    print ('calling url: {}'.format(url))
    
    c = pycurl.Curl()
    c.setopt(c.URL, url)
    c.setopt(c.HTTPHEADER, ['objective: 10000'])
    c.perform()
    
