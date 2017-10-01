package com.theorangehub.hbdvbot.commands;

import com.theorangehub.hbdvbot.HBDVBOT;
import com.theorangehub.hbdvbot.data.HbdvData;
import com.theorangehub.hbdvbot.modules.CommandRegistry;
import com.theorangehub.hbdvbot.modules.Event;
import com.theorangehub.hbdvbot.modules.Module;
import com.theorangehub.hbdvbot.modules.commands.CommandPermission;
import com.theorangehub.hbdvbot.modules.commands.SimpleCommand;
import com.theorangehub.hbdvbot.utils.HbdvUtils;
import bsh.Interpreter;
import com.rethinkdb.RethinkDB;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.entities.MessageEmbed;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import static br.com.brjdevs.java.utils.extensions.CollectionUtils.random;
import static br.com.brjdevs.java.utils.strings.StringUtils.SPLIT_PATTERN;

@Module
public class OwnerCommand {
    private interface Evaluator {
        Object eval(GuildMessageReceivedEvent event, String code);
    }

    private static final String[] sleepQuotes = {
        "*vai dormir*",
        "*sonha com dragões*",
        "*guarda as fichas*",
        "*guarda o diário*",
        "*coloca os dados no bolso*",
        "*pega o diário*"
    };

    @Event
    public static void owner(CommandRegistry cr) {
        Map<String, Evaluator> evals = new HashMap<>();
        evals.put("js", (event, code) -> {
            ScriptEngine script = new ScriptEngineManager().getEngineByName("nashorn");
            script.put("bot", HBDVBOT.getInstance());
            script.put("db", HbdvData.db());
            script.put("jda", event.getJDA());
            script.put("event", event);
            script.put("guild", event.getGuild());
            script.put("channel", event.getChannel());
            script.put("r", RethinkDB.r);
            script.put("resty", HbdvUtils.resty);
            script.put("conn", HbdvData.conn());

            try {
                return script.eval(String.join(
                    "\n",
                    "load(\"nashorn:mozilla_compat.js\");",
                    "imports = new JavaImporter(java.util, java.io, java.net);",
                    "(function() {",
                    "with(imports) {",
                    code,
                    "}",
                    "})()"
                ));
            } catch (Exception e) {
                return e;
            }
        });

        evals.put("bsh", (event, code) -> {
            Interpreter interpreter = new Interpreter();
            try {
                interpreter.set("bot", HBDVBOT.getInstance());
                interpreter.set("db", HbdvData.db());
                interpreter.set("jda", event.getJDA());
                interpreter.set("event", event);
                interpreter.set("guild", event.getGuild());
                interpreter.set("channel", event.getChannel());
                interpreter.set("r", RethinkDB.r);
                interpreter.set("resty", HbdvUtils.resty);
                interpreter.set("conn", HbdvData.conn());

                return interpreter.eval(String.join(
                    "\n",
                    "import *;",
                    code
                ));
            } catch (Exception e) {
                return e;
            }
        });

        cr.register("owner", new SimpleCommand() {
            @Override
            public void call(GuildMessageReceivedEvent event, String content, String[] args) {
                if (args.length < 1) {
                    onHelp(event);
                    return;
                }

                String option = args[0];

                if (option.equals("shutdown")) {
                    try {
                        event.getChannel().sendMessage(random(sleepQuotes)).complete();
                    } catch (Exception ignored) { }

                    System.exit(0);
                    return;
                }

                if (args.length < 2) {
                    onHelp(event);
                    return;
                }

                String value = args[1];
                //1 arg

                String[] values = SPLIT_PATTERN.split(value, 2);
                if (values.length < 2) {
                    onHelp(event);
                    return;
                }

                String k = values[0], v = values[1];
                //2 args

                if (option.equals("eval")) {
                    Evaluator evaluator = evals.get(k);
                    if (evaluator == null) {
                        onHelp(event);
                        return;
                    }

                    Object result = evaluator.eval(event, v);
                    boolean errored = result instanceof Throwable;

                    event.getChannel().sendMessage(new EmbedBuilder()
                        .setAuthor(
                            "Executado " + (errored ? "e falhou" : "com sucesso"), null,
                            event.getAuthor().getAvatarUrl()
                        )
                        .setColor(errored ? Color.RED : Color.GREEN)
                        .setDescription(
                            result == null ? "Executado com sucesso e nenhum objeto retornado." : ("Executado " + (errored ? "e falhou com a seguinte exceção: " : "com sucesso e retornou: ") + result
                                .toString()))
                        .setFooter("Executado por: " + event.getAuthor().getName(), null)
                        .build()
                    ).queue();

                    return;
                }

                onHelp(event);
            }

            @Override
            public String[] splitArgs(String content) {
                return SPLIT_PATTERN.split(content, 2);
            }

            @Override
            public MessageEmbed help(GuildMessageReceivedEvent event) {
                return helpEmbed(event, "Comando Owner")
                    .setDescription("`!owner shutdown`: Desliga o Bot\n" +
                        "`!owner eval <bsh/js> <código>` - Executa um pedaço de código."
                    )
                    .build();
            }

            @Override
            public CommandPermission permission() {
                return CommandPermission.OWNER;
            }

        });
    }
}