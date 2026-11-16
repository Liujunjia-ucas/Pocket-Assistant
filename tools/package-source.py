"""Package exactly the reviewed manifest; refuse stale checks or existing targets."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import zipfile
from importlib.util import spec_from_file_location, module_from_spec

ROOT=Path(__file__).resolve().parents[1]
def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("--output",required=True,help="New clean source directory outside the working tree")
    args=parser.parse_args()
    output=Path(args.output).resolve()
    if output==ROOT or ROOT in output.parents or output.exists():
        raise SystemExit("Choose a new directory outside the working tree; no overwrite is performed")
    spec=spec_from_file_location("publication_audit",ROOT/"tools/audit-publication.py")
    module=module_from_spec(spec);spec.loader.exec_module(module)
    report,inventory=module.audit()
    if report["errors"]:raise SystemExit("Publication audit failed")
    archive=output.with_name(output.name+"-source.zip")
    checksum=archive.with_suffix(".zip.sha256")
    if archive.exists() or checksum.exists():raise SystemExit("Output archive already exists")
    output.mkdir(parents=True)
    paths=[entry["path"] for entry in inventory["files"]]+[module.MANIFEST]
    for rel in paths:
        destination=output/rel;destination.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(ROOT/rel,destination)
    with zipfile.ZipFile(archive,"w",compression=zipfile.ZIP_DEFLATED,compresslevel=9) as bundle:
        for rel in paths:
            data=(output/rel).read_bytes()
            info=zipfile.ZipInfo(output.name+"/"+rel,date_time=(2026,11,16,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            bundle.writestr(info,data)
    with zipfile.ZipFile(archive) as bundle:
        if bundle.testzip() is not None:raise SystemExit("Archive integrity check failed")
        if len(bundle.namelist())!=len(paths):raise SystemExit("Archive file count mismatch")
        for rel in paths:
            if bundle.read(output.name+"/"+rel)!=(output/rel).read_bytes():raise SystemExit("Archive data mismatch")
    sha=hashlib.sha256(archive.read_bytes()).hexdigest()
    checksum.write_text(sha+"  "+archive.name+"\n",encoding="utf-8")
    print(json.dumps({"directory":str(output),"archive":str(archive),"files":len(paths),"archiveBytes":archive.stat().st_size,"sha256":sha},ensure_ascii=False,indent=2))
if __name__=="__main__":main()
