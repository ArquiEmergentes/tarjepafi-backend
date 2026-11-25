"""Writes the endpoint inventory of the deployed API (from its OpenAPI document) as a Markdown table.

    python scripts/evidence/endpoint_inventory.py https://<api host> > inventory.md

The OpenAPI document is public (Swagger UI shows it), so no credentials are needed.
"""
import json
import sys
import urllib.request

ORDER = ["get", "post", "put", "patch", "delete"]


def main():
    base = sys.argv[1].rstrip("/")
    with urllib.request.urlopen(base + "/v3/api-docs", timeout=60) as response:
        document = json.load(response)
    rows = []
    for path, operations in document["paths"].items():
        for method in ORDER:
            operation = operations.get(method)
            if operation is None:
                continue
            tag = (operation.get("tags") or ["-"])[0]
            summary = (operation.get("summary") or "").replace("|", "/")
            responses = ", ".join(sorted(operation.get("responses", {}).keys()))
            rows.append((tag, path, method.upper(), summary, responses))
    rows.sort(key=lambda row: (row[0], row[1], ORDER.index(row[2].lower())))
    print(f"{len(rows)} operations in {len({row[0] for row in rows})} groups\n")
    print("| Group | Method | Path | Summary | Documented responses |")
    print("|---|---|---|---|---|")
    for tag, path, method, summary, responses in rows:
        print(f"| {tag} | {method} | `{path}` | {summary} | {responses} |")


if __name__ == "__main__":
    main()
