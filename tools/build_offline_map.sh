#!/usr/bin/env bash
set -euo pipefail
mkdir -p map-work map-output "$HOME/.openstreetmap/osmosis/plugins"
curl -fL --retry 3 https://repo.maven.apache.org/maven2/org/mapsforge/mapsforge-map-writer/0.21.0/mapsforge-map-writer-0.21.0-jar-with-dependencies.jar -o "$HOME/.openstreetmap/osmosis/plugins/mapsforge-map-writer.jar"
curl -fL --retry 3 https://download.geofabrik.de/europe/united-kingdom/scotland-latest.osm.pbf -o map-work/scotland.osm.pbf
osmium extract --bbox=-3.95,55.60,-2.55,56.40 --strategy=complete_ways map-work/scotland.osm.pbf -o map-output/edinburgh-dunfermline-source.osm.pbf --overwrite
export JAVACMD_OPTIONS=-Xmx4g JAVA_OPTS=-Xmx4g
osmosis --read-pbf file=map-output/edinburgh-dunfermline-source.osm.pbf --mapfile-writer file=map-output/edinburgh-dunfermline.map bbox=55.60,-3.95,56.40,-2.55 type=hd
python3 - <<'PY'
from pathlib import Path
import hashlib,json,datetime
p=Path('map-output/edinburgh-dunfermline.map')
with p.open('rb') as f: assert f.read(20)==b'mapsforge binary OSM'
d={'built_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'bbox_west_south_east_north':[-3.95,55.60,-2.55,56.40],'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'attribution':'© OpenStreetMap contributors','license':'ODbL 1.0','source':'https://download.geofabrik.de/europe/united-kingdom/scotland.html','note':'Regional rectangle; not an exactly calculated 20 km municipal boundary buffer.'}
Path('map-output/MAP-INFO.json').write_text(json.dumps(d,indent=2),encoding='utf-8')
PY
cat > map-output/READ-ME.txt <<'TEXT'
Import only edinburgh-dunfermline.map into BOTH phones. Not the ZIP or PBF.
Data © OpenStreetMap contributors, ODbL 1.0.
https://www.openstreetmap.org/copyright
https://opendatacommons.org/licenses/odbl/1-0/
Matching source extract included. Check coverage before use. Not safety-certified navigation.
TEXT
