import pycurl
import json
import sys
from pathlib import Path
from io import BytesIO



NB_QUERY = 400
TRACES_FILE = Path('tracesSingle.json')
URL = 'http://localhost:16686/api/traces?service=box-8080&limit={}'.format(NB_QUERY)




buffer = BytesIO()
c = pycurl.Curl()
c.setopt(c.URL, URL)
c.setopt(c.WRITEDATA, buffer)
c.perform()
c.close()

results = json.loads(buffer.getvalue())
with TRACES_FILE.open('w') as f:
    json.dump(results, f)
