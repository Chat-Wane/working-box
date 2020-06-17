import json
import sys
from pathlib import Path
from io import BytesIO



NB_QUERY = 400
TRACES_FILE = Path('tracesSingle.json')
URL = 'http://localhost:16686/api/traces?service=box-8080&limit={}'.format(NB_QUERY)



if not TRACES_FILE.is_file():
    print ("/!\ The file you try to analyze does not exist.")
    sys.exit(0)

with TRACES_FILE.open('r') as f:
    results = json.load(f)

changes = []
rewritten = []
for trace in results['data']:
    for span in trace['spans']:
        if (span['operationName'] == 'handle'):
            start = span['startTime']
            hasTagRewritten = False
            for tag in span['tags']:
                if (tag['key'] == 'isLastInputKept'):
                    changes.append((start, tag['value']))
                if (tag['key'] == 'isLastInputRewritten'):
                    hasTagRewritten = True
                    rewritten.append((start, tag['value']))
            if not hasTagRewritten:
                rewritten.append(start, False)
                
changes = sorted(changes, key=lambda x: x[0])
rewritten = sorted(rewritten, key=lambda x: x[0])

# print (changes)
# print ()
# print (rewritten)

for i in range(0, len(changes)):
    meow = 1 if changes[i][1] else 0;
    maw = 1 if rewritten[i][1] else 0;
    print ("{}\t{}".format(meow, maw))

