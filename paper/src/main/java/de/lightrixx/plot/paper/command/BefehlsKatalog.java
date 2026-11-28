package de.lightrixx.plot.paper.command;

import java.util.List;

/** Aus scripts/project.json erzeugt; mit sync-standard.py aktualisieren. */
public final class BefehlsKatalog {
    private BefehlsKatalog() { }
    public static final String WURZEL = "simpleplots";
    public static final List<FamilienBefehle.Eintrag> EINTRAEGE = List.of(
            new FamilienBefehle.Eintrag("help", "help [Seite]", List.of("simpleplots.help"), List.of(), false, 0, 1, List.of("help"), List.of("hilfe")),
            new FamilienBefehle.Eintrag("version", "version", List.of("simpleplots.version"), List.of(), false, 0, 0, List.of("version"), List.of()),
            new FamilienBefehle.Eintrag("world", "world [Welt]", List.of("simpleplots.use"), List.of(), true, 0, 1, List.of("world"), List.of()),
            new FamilienBefehle.Eintrag("back", "back", List.of("simpleplots.use"), List.of(), true, 0, 0, List.of("back"), List.of()),
            new FamilienBefehle.Eintrag("claim", "claim", List.of("simpleplots.use", "simpleplots.claim"), List.of(), true, 0, 0, List.of("claim"), List.of()),
            new FamilienBefehle.Eintrag("auto", "auto", List.of("simpleplots.use", "simpleplots.claim"), List.of(), true, 0, 0, List.of("auto"), List.of()),
            new FamilienBefehle.Eintrag("home", "home [Nummer]", List.of("simpleplots.use"), List.of(), true, 0, 1, List.of("home"), List.of()),
            new FamilienBefehle.Eintrag("visit", "visit <Spieler|Plot-ID|#Name> [Nummer]", List.of("simpleplots.use"), List.of(), true, 1, 9999, List.of("visit"), List.of()),
            new FamilienBefehle.Eintrag("list", "list", List.of("simpleplots.use"), List.of(), true, 0, 0, List.of("list"), List.of()),
            new FamilienBefehle.Eintrag("info", "info", List.of("simpleplots.use"), List.of(), true, 0, 0, List.of("info"), List.of()),
            new FamilienBefehle.Eintrag("menu", "menu", List.of("simpleplots.use"), List.of(), true, 0, 0, List.of("menu"), List.of()),
            new FamilienBefehle.Eintrag("discover", "discover [Kategorie] [new|popular]", List.of("simpleplots.use"), List.of(), true, 0, 2, List.of("discover"), List.of()),
            new FamilienBefehle.Eintrag("profile", "profile <name|description|category> <Wert>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 2, 9999, List.of("profile"), List.of()),
            new FamilienBefehle.Eintrag("flag", "flag <doors|switches> <on|off>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 2, 2, List.of("flag"), List.of()),
            new FamilienBefehle.Eintrag("sethome", "sethome [off]", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 0, 1, List.of("sethome"), List.of()),
            new FamilienBefehle.Eintrag("merge", "merge <Richtung> [confirm]", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 1, 2, List.of("merge"), List.of()),
            new FamilienBefehle.Eintrag("unlink", "unlink [confirm]", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 0, 1, List.of("unlink"), List.of("unmerge")),
            new FamilienBefehle.Eintrag("biome", "biome <Biom|off>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 1, 1, List.of("biome"), List.of()),
            new FamilienBefehle.Eintrag("like", "like", List.of("simpleplots.use"), List.of(), true, 0, 0, List.of("like"), List.of()),
            new FamilienBefehle.Eintrag("unlike", "unlike", List.of("simpleplots.use"), List.of(), true, 0, 0, List.of("unlike"), List.of()),
            new FamilienBefehle.Eintrag("submit", "submit", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 0, 0, List.of("submit"), List.of()),
            new FamilienBefehle.Eintrag("vote", "vote", List.of("simpleplots.use"), List.of(), true, 0, 0, List.of("vote"), List.of()),
            new FamilienBefehle.Eintrag("withdraw", "withdraw", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 0, 0, List.of("withdraw"), List.of()),
            new FamilienBefehle.Eintrag("ban", "ban <Spieler>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 1, 1, List.of("ban"), List.of()),
            new FamilienBefehle.Eintrag("unban", "unban <Spieler>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 1, 1, List.of("unban"), List.of()),
            new FamilienBefehle.Eintrag("trust", "trust <Spieler>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 1, 1, List.of("trust"), List.of()),
            new FamilienBefehle.Eintrag("untrust", "untrust <Spieler>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 1, 1, List.of("untrust"), List.of()),
            new FamilienBefehle.Eintrag("deny", "deny <Spieler>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 1, 1, List.of("deny"), List.of()),
            new FamilienBefehle.Eintrag("undeny", "undeny <Spieler>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 1, 1, List.of("undeny"), List.of()),
            new FamilienBefehle.Eintrag("kick", "kick <Spieler>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 1, 1, List.of("kick"), List.of()),
            new FamilienBefehle.Eintrag("add", "add <Spieler>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 1, 1, List.of("add"), List.of()),
            new FamilienBefehle.Eintrag("unadd", "unadd <Spieler>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 1, 1, List.of("unadd"), List.of()),
            new FamilienBefehle.Eintrag("storage", "storage <Spieler>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 1, 1, List.of("storage"), List.of()),
            new FamilienBefehle.Eintrag("unstore", "unstore <Spieler>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 1, 1, List.of("unstore"), List.of()),
            new FamilienBefehle.Eintrag("transfer", "transfer <Spieler|cancel>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 1, 1, List.of("transfer"), List.of()),
            new FamilienBefehle.Eintrag("accept", "accept <Plot-ID>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 1, 1, List.of("accept"), List.of()),
            new FamilienBefehle.Eintrag("decline", "decline <Plot-ID>", List.of("simpleplots.use", "simpleplots.manage"), List.of(), true, 1, 1, List.of("decline"), List.of()),
            new FamilienBefehle.Eintrag("bookmark", "bookmark [list|add|remove|go] [Name]", List.of("simpleplots.use", "simpleplots.bookmark"), List.of(), true, 0, 9999, List.of("bookmark"), List.of()),
            new FamilienBefehle.Eintrag("border", "border [Sekunden|off]", List.of("simpleplots.use", "simpleplots.border"), List.of(), true, 0, 1, List.of("border"), List.of()),
            new FamilienBefehle.Eintrag("fly", "fly [on|off]", List.of("simpleplots.use"), List.of("simpleplots.fly", "simpleplots.admin"), true, 0, 1, List.of("fly"), List.of()),
            new FamilienBefehle.Eintrag("worlds", "worlds", List.of("simpleplots.use"), List.of(), false, 0, 0, List.of("worlds"), List.of()),
            new FamilienBefehle.Eintrag("in", "in <Welt> <Unterbefehl> [Argumente]", List.of("simpleplots.use"), List.of(), false, 2, 9999, List.of("in"), List.of()),
            new FamilienBefehle.Eintrag("contest", "contest [Unterbefehl] [Argumente]", List.of("simpleplots.use"), List.of(), false, 0, 9999, List.of("contest"), List.of()),
            new FamilienBefehle.Eintrag("admin", "admin [Unterbefehl] [Argumente]", List.of(), List.of("simpleplots.admin", "simpleplots.reload", "simpleplots.diagnostics", "simpleplots.update", "simpleplots.admin.bypass", "simpleplots.admin.list", "simpleplots.admin.inspect", "simpleplots.admin.visit", "simpleplots.admin.claim", "simpleplots.admin.unclaim", "simpleplots.admin.setowner", "simpleplots.admin.role", "simpleplots.admin.profile", "simpleplots.admin.censor", "simpleplots.admin.remove", "simpleplots.admin.restore", "simpleplots.admin.setup", "simpleplots.admin.contest"), false, 0, 9999, List.of("admin"), List.of()),
            new FamilienBefehle.Eintrag("setup", "setup", List.of("simpleplots.admin.setup"), List.of(), false, 0, 0, List.of("admin", "setup"), List.of()),
            new FamilienBefehle.Eintrag("reload", "reload", List.of("simpleplots.reload"), List.of(), false, 0, 0, List.of("admin", "reload"), List.of()),
            new FamilienBefehle.Eintrag("diagnostics", "diagnostics", List.of("simpleplots.diagnostics"), List.of(), false, 0, 0, List.of("admin", "diagnostics"), List.of()),
            new FamilienBefehle.Eintrag("update", "update [status|check|apply confirm]", List.of("simpleplots.update"), List.of(), false, 0, 2, List.of("admin", "update"), List.of()));
}
