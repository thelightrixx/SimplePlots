"""Prüfbarer Befehlsvertrag und gemeinsame Paper-Bedienung der Plugin-Familie."""
import json
from html import escape
from pathlib import Path
import re


REQUIRED = {"help", "version", "reload", "diagnostics", "update"}
TEMPLATES = {
    "FamilienBefehle.java.template": ("src/main/java", "package"),
    "FamilienBefehleTest.java.template": ("src/test/java", "package"),
    "PluginUpdater.java.template": ("src/main/java", "updaterPackage"),
    "UpdateEinstellungen.java.template": ("src/main/java", "updaterPackage"),
    "UpdateDatei.java.template": ("src/main/java", "updaterPackage"),
    "UpdateReloadTest.java.template": ("src/test/java", "updaterPackage"),
}


def contract(manifest):
    settings = manifest.get("commands")
    if settings is None:
        if manifest["name"] != "PunishSystem":
            raise ValueError("Der gemeinsame Befehlsvertrag fehlt.")
        return None
    if settings["root"] != manifest["name"].lower():
        raise ValueError("Der Hauptbefehl muss dem kleingeschriebenen Projektnamen entsprechen.")
    if not re.fullmatch(r"de\.lightrixx\.[a-z][a-z0-9]*(?:\.[a-z][a-z0-9]*)+", settings["package"]):
        raise ValueError("Ungültiges Java-Paket des Befehlsvertrags.")
    entries = settings["entries"]
    names = set()
    for entry in entries:
        for name in [entry["name"], *entry.get("aliases", [])]:
            if not re.fullmatch(r"[a-zäöüß][a-z0-9äöüß-]*", name) or name in names:
                raise ValueError("Ungültiger oder doppelter Unterbefehl: " + name)
            names.add(name)
        if not entry["syntax"].startswith(entry["name"]):
            raise ValueError("Die Syntax gehört nicht zum Unterbefehl: " + entry["name"])
        if not 0 <= entry["min"] <= entry["max"] <= 2147483647:
            raise ValueError("Ungültige Argumentgrenzen: " + entry["name"])
        if not entry["description"].strip() or not entry["route"]:
            raise ValueError("Beschreibung oder Weiterleitung fehlt: " + entry["name"])
    if not REQUIRED <= {entry["name"] for entry in entries}:
        raise ValueError("Gemeinsame Unterbefehle fehlen: " + ", ".join(sorted(REQUIRED - names)))
    return settings


def java_string(value):
    return json.dumps(value, ensure_ascii=False)


def java_list(values):
    return "List.of(" + ", ".join(java_string(value) for value in values) + ")"


def permission_definitions(text):
    """Liest die im Projekt verwendete plugin.yml-Schreibweise ohne zusätzliche Abhängigkeit."""
    section = re.search(r"^permissions:\s*\n(.*?)(?=^\S|\Z)", text, re.M | re.S)
    result = {}
    if section is None:
        return result
    for match in re.finditer(r"^  ([a-z0-9.*_-]+):([^\n]*)(.*?)(?=^  [a-z0-9.*_-]+:|\Z)", section[1], re.M | re.S):
        node, inline, body = match.groups()
        default = re.search(r"\bdefault:\s*(true|false|op)(?=[,}\s]|$)", inline + body)
        description = re.search(r"\bdescription:\s*(.*?)(?:,\s*default:|\n|$)", inline + body)
        if default is None or node in result:
            raise ValueError("Fehlender Standardwert oder doppeltes Recht: " + node)
        result[node] = {"default": default[1], "description": description[1].strip().strip('\"\'') if description else "",
                        "children": dict(re.findall(r"^      ([a-z0-9.*_-]+): (true|false)\s*$", body, re.M))}
    return result


def permission_table(root, manifest):
    settings = contract(manifest)
    if settings is None:
        return ""
    permissions = permission_definitions((root / "paper/src/main/resources/plugin.yml").read_text(encoding="utf-8"))
    lines = ["<!-- PERMISSION-STANDARD -->",
             '<p><code>true</code> ist standardmäßig erlaubt, <code>op</code> gilt für Operatoren. Rechte mit <code>false</code> müssen ausdrücklich vergeben werden. Administrative Bündel enthalten keine Schutzumgehung.</p>',
             '<div class="tbl"><table><thead><tr><th scope="col">Recht</th><th scope="col">Standard</th><th scope="col">Aufgabe</th><th scope="col">Enthaltene Rechte</th></tr></thead><tbody>']
    for name, definition in permissions.items():
        children = ", ".join('<code>' + escape(node) + '</code>' + (" (verweigert)" if value == "false" else "")
                             for node, value in definition["children"].items())
        if len(definition["children"]) > 3:
            children = '<details><summary>' + str(len(definition["children"])) + ' Rechte</summary><p>' + children + '</p></details>'
        lines.append('<tr><td><code>' + escape(name) + '</code></td><td><code>' + definition["default"] +
                     '</code></td><td>' + escape(definition["description"]) + '</td><td>' + (children or "–") + '</td></tr>')
    return "\n".join(lines + ['</tbody></table></div>', '<!-- /PERMISSION-STANDARD -->'])


def check_permissions(definitions, namespace):
    protected = {name for name in definitions if name.endswith((".bypass", ".exempt", ".override"))}
    for name in protected | {name for name in definitions if name.endswith(".*")}:
        if definitions[name]["default"] != "false":
            raise ValueError("Schutz- und Wildcardrechte müssen ausdrücklich vergeben werden: " + name)

    def children(node, visiting):
        if node in visiting:
            raise ValueError("Zyklische Rechtevererbung: " + node)
        if node not in definitions:
            raise ValueError("Nicht deklariertes Kindrecht: " + node)
        result = set()
        for name, value in definitions[node]["children"].items():
            if name not in definitions:
                raise ValueError("Nicht deklariertes Kindrecht: " + name)
            if value == "true":
                result.add(name)
                result.update(children(name, visiting | {node}))
        return result

    for name in definitions:
        children(name, set())
    admin = namespace + ".admin"
    if admin not in definitions:
        raise ValueError("Das gemeinsame Verwaltungsbündel fehlt: " + admin)
    if protected & children(admin, set()):
        raise ValueError("Verwaltungsrechte dürfen keine Schutzumgehung enthalten.")


def replace_permission_table(text, root, manifest):
    if contract(manifest) is None:
        return text
    section = re.search(r'(<section\b[^>]*data-id="rechte"[^>]*>)(.*?)(</section>)', text, re.S)
    if section is None:
        raise ValueError("Das Kapitel Berechtigungen fehlt.")
    body = section[2]
    managed = re.compile(r"<!-- PERMISSION-STANDARD -->.*?<!-- /PERMISSION-STANDARD -->", re.S)
    replacement = permission_table(root, manifest)
    if managed.search(body):
        if len(managed.findall(body)) != 1:
            raise ValueError("Die Berechtigungsübersicht ist mehrfach vorhanden.")
        body = managed.sub(lambda _: replacement, body)
    else:
        original = re.compile(r'<div class="tbl">\s*<table\b[^>]*>.*?</table>\s*</div>', re.S)
        if len(original.findall(body)) != 1:
            raise ValueError("Die Berechtigungsübersicht fehlt oder ist mehrfach vorhanden.")
        body = original.sub(lambda _: replacement, body)
    return text[:section.start(2)] + body + text[section.end(2):]


def command_table(settings):
    root = "/" + settings["root"]
    lines = ["<!-- COMMAND-STANDARD -->",
             '<p><code>' + root + '</code> und <code>' + root + ' help [Seite]</code> zeigen die für deine Rechte verfügbaren Befehle, zehn pro Seite. TAB bietet nur erlaubte Befehle und passende Argumente an. Ein Aufruf ohne Unterbefehl führt keine Spielaktion aus.</p>',
             '<p>Verwaltung: <code>' + root + ' version</code>, <code>' + root + ' reload</code>, <code>' + root + ' diagnostics</code> und <code>' + root + ' update</code>. Der Reload prüft auch die Update-Einstellungen. Ein Download braucht <code>update apply confirm</code>; übernommen wird die JAR beim nächsten normalen Serverstart.</p>',
             '<div class="tbl"><table data-command-index id="commandIndex"><thead><tr><th scope="col">Befehl</th><th scope="col">Wirkung</th><th scope="col">Recht</th><th scope="col">Ausführung</th></tr></thead><tbody>']
    for entry in settings["entries"]:
        rights = ['<code>' + escape(right) + '</code>' for right in entry["permissions"]]
        alternatives = ['<code>' + escape(right) + '</code>' for right in entry.get("anyPermissions", [])]
        if alternatives:
            rights.append('passendes <a href="#/rechte">Verwaltungsrecht</a>' if len(alternatives) > 3 else "(" + " oder ".join(alternatives) + ")")
        lines.append('<tr><td><code>' + escape(root + " " + entry["syntax"]) + '</code></td><td>' +
                     escape(entry["description"]) + '</td><td>' + (" und ".join(rights) or "öffentlich") +
                     '</td><td>' + ("Nur im Spiel" if entry.get("playerOnly") else "Spiel und Konsole") + '</td></tr>')
    return "\n".join(lines + ['</tbody></table></div>', '<!-- /COMMAND-STANDARD -->'])


def command_readme(settings):
    root = "/" + settings["root"]
    return ("<!-- COMMAND-QUICKSTART -->\nMit `" + root + "` oder `" + root + " help [Seite]` "
            "siehst du die für deine Rechte verfügbaren Befehle. Die Verwaltung folgt `" + root +
            " version`, `reload`, `diagnostics` und `update`. Ein Aufruf ohne Unterbefehl zeigt die Hilfe.\n"
            "<!-- /COMMAND-QUICKSTART -->")


def replace_command_readme(text, manifest):
    settings = contract(manifest)
    if settings is None:
        return text
    replacement = command_readme(settings)
    managed = re.compile(r"<!-- COMMAND-QUICKSTART -->.*?<!-- /COMMAND-QUICKSTART -->", re.S)
    if text.count("<!-- COMMAND-QUICKSTART -->") != text.count("<!-- /COMMAND-QUICKSTART -->"):
        raise ValueError("Unvollständige Markierung der Befehlsübersicht in der README.")
    if managed.search(text):
        if len(managed.findall(text)) != 1:
            raise ValueError("Befehlsübersicht ist mehrfach vorhanden.")
        return managed.sub(lambda _: replacement, text)
    if text.count("## Benutzen\n") != 1:
        raise ValueError("Der Abschnitt Benutzen fehlt in der README.")
    return text.replace("## Benutzen\n", "## Benutzen\n\n" + replacement + "\n", 1)


def replace_command_table(text, manifest):
    settings = contract(manifest)
    if settings is None:
        return text
    replacement = command_table(settings)
    managed = re.compile(r"<!-- COMMAND-STANDARD -->.*?<!-- /COMMAND-STANDARD -->", re.S)
    if text.count("<!-- COMMAND-STANDARD -->") != text.count("<!-- /COMMAND-STANDARD -->"):
        raise ValueError("Unvollständige Markierung der Befehlsreferenz.")
    if managed.search(text):
        if len(managed.findall(text)) != 1:
            raise ValueError("Befehlsreferenz ist mehrfach vorhanden.")
        return managed.sub(lambda _: replacement, text)
    originals = list(re.finditer(r'<(div|table)\b[^>]*\bid="commandIndex"[^>]*>', text))
    if len(originals) != 1:
        raise ValueError("Die Befehlsreferenz fehlt oder ist mehrfach vorhanden.")
    original = originals[0]
    start = original.start()
    # Eine vorhandene Tabellenhülle mit ersetzen, damit keine unnötigen Scrollbereiche entstehen.
    if original[1] == "table":
        wrapper = re.search(r'<div class="tbl">\s*$', text[:start])
        if wrapper:
            start = wrapper.start()
    tag = "div" if start != original.start() else original[1]
    depth = 0
    for match in re.finditer(r'</?' + tag + r'\b[^>]*>', text[start:]):
        depth += -1 if match[0].startswith("</") else 1
        if depth == 0:
            end = start + match.end()
            return text[:start] + replacement + text[end:]
    raise ValueError("Die Befehlsreferenz ist nicht geschlossen.")


def runtime_files(reference, manifest):
    settings = contract(manifest)
    if settings is None:
        return {}
    package = settings["package"]
    result = {}
    for name, (source, package_key) in TEMPLATES.items():
        target_package = settings.get(package_key)
        if target_package is None:
            continue
        if not re.fullmatch(r"de\.lightrixx\.[a-z][a-z0-9]*(?:\.[a-z][a-z0-9]*)+", target_package):
            raise ValueError("Ungültiges Java-Paket der gemeinsamen Quellen.")
        text = (reference / "scripts/templates" / name).read_text(encoding="utf-8")
        target = Path("paper") / source / target_package.replace(".", "/") / name.removesuffix(".template")
        result[str(target).replace("\\", "/")] = text.replace("@PACKAGE@", target_package).encode("utf-8")
    lines = ["package " + package + ";", "", "import java.util.List;", "",
             "/** Aus scripts/project.json erzeugt; mit sync-standard.py aktualisieren. */",
             "public final class BefehlsKatalog {", "    private BefehlsKatalog() { }",
             "    public static final String WURZEL = " + java_string(settings["root"]) + ";",
             "    public static final List<FamilienBefehle.Eintrag> EINTRAEGE = List.of("]
    rows = []
    for entry in settings["entries"]:
        rows.append("            new FamilienBefehle.Eintrag(" + ", ".join([
            java_string(entry["name"]), java_string(entry["syntax"]), java_list(entry["permissions"]), java_list(entry.get("anyPermissions", [])),
            str(entry.get("playerOnly", False)).lower(), str(entry["min"]), str(entry["max"]),
            java_list(entry["route"]), java_list(entry.get("aliases", []))]) + ")")
    lines += [",\n".join(rows) + ");", "}", ""]
    target = "paper/src/main/java/" + package.replace(".", "/") + "/BefehlsKatalog.java"
    result[target] = "\n".join(lines).encode("utf-8")
    return result


def check(root):
    manifest = json.loads((root / "scripts/project.json").read_text(encoding="utf-8"))
    settings = contract(manifest)
    if settings is not None:
        resource = root / settings["messages"]
        if not resource.resolve().is_relative_to(root.resolve()):
            raise ValueError("Befehlstexte liegen außerhalb des Projekts.")
        keys = re.findall(r"^\s*standard-([a-z-]+):", resource.read_text(encoding="utf-8"), re.M)
        expected = {"kopf", "fuss", "leer", "syntax", "seite", "unbekannt", "kein-recht", "nur-spieler"}
        expected.update(entry["name"] for entry in settings["entries"])
        if not expected <= set(keys) or any(keys.count(key) != 1 for key in expected):
            raise ValueError("Die Befehlstexte passen nicht zum Befehlsvertrag: " + str(resource))
        handbook = (root / "docs/index.html").read_text(encoding="utf-8")
        if command_table(settings) not in handbook:
            raise ValueError("Die Befehlsreferenz weicht vom Befehlsvertrag ab.")
        if permission_table(root, manifest) not in handbook:
            raise ValueError("Die Berechtigungsübersicht weicht von plugin.yml ab.")
        if command_readme(settings) not in (root / "README.md").read_text(encoding="utf-8"):
            raise ValueError("Die Befehlsübersicht in der README weicht vom Befehlsvertrag ab.")
    for relative, expected in runtime_files(root, manifest).items():
        path = root / relative
        if not path.is_file() or path.read_bytes().replace(b"\r\n", b"\n") != expected.replace(b"\r\n", b"\n"):
            raise ValueError("Gemeinsamer Befehlscode weicht ab: " + relative)
    for artifact in manifest["artifacts"]:
        if artifact["metadata"] != "plugin.yml":
            continue
        path = root / artifact["module"] / "src/main/resources/plugin.yml"
        if not path.is_file() and artifact["module"] == "paper-1.21":
            path = root / "paper/src/main/resources/plugin.yml"
        text = path.read_text(encoding="utf-8")
        commands = re.search(r"^commands:\s*\n(.*?)(?=^\S|\Z)", text, re.M | re.S)
        names = set(re.findall(r"^  ([a-z][a-z0-9-]*):", commands[1], re.M)) if commands else set()
        if settings is None:
            # Reload und Update werden ausschließlich auf dem Proxy ausgeführt.
            if not {"punish-help", "punish-version", "punish-diagnostics"} <= names:
                raise ValueError("PunishSystem-Verwaltungsbefehle sind unvollständig: " + str(path))
            continue
        if settings["root"] not in names:
            raise ValueError("Der Hauptbefehl fehlt: " + str(path))
        definitions = permission_definitions(text)
        permissions = set(definitions)
        check_permissions(definitions, settings["root"])
        for entry in settings["entries"]:
            missing = set(entry["permissions"] + entry.get("anyPermissions", [])) - permissions
            if missing:
                raise ValueError("Befehlsrecht fehlt in " + str(path) + ": " + ", ".join(sorted(missing)))
