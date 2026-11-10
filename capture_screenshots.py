"""Capture the screenshots of the SAP Backup & Restore Readiness portal.

Logs in over HTTP, saves each page as HTML with a <base href> pointing at
the running application, and lets headless Chrome render it to PNG.
"""
import http.cookiejar
import os
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = "http://localhost:8084"
ROOT = os.path.dirname(os.path.abspath(__file__))
SHOTS = os.path.join(ROOT, "docs", "screenshots")
WORK = os.path.join(ROOT, ".screenshot-work")
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

PAGES = [
    ("/login", "login", False),
    ("/", "readiness-dashboard", True),
    ("/systems/PRD", "system-prd-critical", True),
    ("/systems/BW", "system-bw-capacity-risk", True),
    ("/systems/SOL", "system-sol-healthy", True),
    ("/report", "continuity-report", True),
]


class NoRedirect(urllib.request.HTTPRedirectHandler):
    """Keeps Tomcat from rewriting the redirect with a ;jsessionid= path."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def make_opener():
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(
        urllib.request.HTTPCookieProcessor(jar), NoRedirect)
    return opener, jar


def sign_in(opener, jar):
    data = urllib.parse.urlencode(
        {"username": "continuity.lead", "password": "demo1234"}
    ).encode()
    request = urllib.request.Request(BASE + "/login", data=data)
    try:
        opener.open(request, timeout=30)
    except urllib.error.HTTPError as error:
        if error.code not in (302, 303):
            raise
    cookies = [c.name for c in jar]
    if "JSESSIONID" not in cookies:
        raise SystemExit("sign in failed - no session cookie was returned")


def fetch(opener, path):
    with opener.open(BASE + path, timeout=30) as response:
        return response.read().decode("utf-8", "replace")


def with_base(html):
    if "<base " not in html:
        html = html.replace(
            "<head>", '<head>\n  <base href="%s/">' % BASE, 1
        )
    return html


def shoot(name, html):
    os.makedirs(WORK, exist_ok=True)
    os.makedirs(SHOTS, exist_ok=True)
    html_path = os.path.join(WORK, name + ".html")
    with open(html_path, "w", encoding="utf-8") as handle:
        handle.write(with_base(html))
    png = os.path.join(SHOTS, name + ".png")
    url = "file:///" + html_path.replace("\\", "/")
    subprocess.run(
        [
            CHROME,
            "--headless=new",
            "--disable-gpu",
            "--hide-scrollbars",
            "--force-device-scale-factor=1",
            '--window-size=1600,1600',
            "--virtual-time-budget=4000",
            "--screenshot=" + png,
            url,
        ],
        check=True,
        capture_output=True,
    )
    size = os.path.getsize(png) if os.path.exists(png) else 0
    print("%-28s %8d bytes" % (name + ".png", size))


def main():
    opener, jar = make_opener()
    sign_in(opener, jar)
    for path, name, needs_login in PAGES:
        html = fetch(opener, path)
        if "<title>" not in html:
            raise SystemExit("no HTML returned for " + path)
        shoot(name, html)
    print("done")


if __name__ == "__main__":
    main()
