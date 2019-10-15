package org.l2j.gameserver.engine.mission;

import io.github.joealisson.primitive.HashIntMap;
import io.github.joealisson.primitive.IntMap;
import io.github.joealisson.primitive.CHashIntMap;
import org.l2j.gameserver.data.database.data.MissionPlayerData;
import org.l2j.gameserver.model.StatsSet;
import org.l2j.gameserver.model.actor.instance.Player;
import org.l2j.gameserver.model.base.ClassId;
import org.l2j.gameserver.model.dailymission.MissionDataHolder;
import org.l2j.gameserver.model.holders.ItemHolder;
import org.l2j.gameserver.settings.ServerSettings;
import org.l2j.gameserver.util.GameXmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;
import static org.l2j.commons.configuration.Configurator.getSettings;
import static org.l2j.commons.util.Util.isNullOrEmpty;

/**
 * @author Sdw
 */
public class MissionData extends GameXmlReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(MissionData.class);

    private final IntMap<IntMap<MissionPlayerData>> missionsData = new CHashIntMap<>();
    private final IntMap<List<MissionDataHolder>> dailyMissionRewards = new HashIntMap<>();

    private boolean available;

    private MissionData() {
    }

    @Override
    protected Path getSchemaFilePath() {
        return getSettings(ServerSettings.class).dataPackDirectory().resolve("data/xsd/DailyMission.xsd");
    }

    @Override
    public void load() {
        dailyMissionRewards.clear();
        parseDatapackFile("data/DailyMission.xml");
        available = !dailyMissionRewards.isEmpty();
        LOGGER.info("Loaded {} one day rewards.",  dailyMissionRewards.size());
    }

    @Override
    public void parseDocument(Document doc, File f) {
        forEach(doc, "list", listNode -> forEach(listNode, "mission", missionNode -> {
            final StatsSet set = new StatsSet(parseAttributes(missionNode));

            final List<ItemHolder> items = new ArrayList<>(1);

            forEach(missionNode, "reward", itemNode -> {
                final int itemId = parseInteger(itemNode.getAttributes(), "id");
                final int itemCount = parseInteger(itemNode.getAttributes(), "count");
                items.add(new ItemHolder(itemId, itemCount));
            });

            set.set("rewards", items);

            final List<ClassId> classRestriction = new ArrayList<>(1);
            forEach(missionNode, "classes", classesNode -> {
                if(isNullOrEmpty(classesNode.getTextContent())) {
                    return;
                }
                classRestriction.addAll(Arrays.stream(classesNode.getTextContent().split(" ")).map(id -> ClassId.getClassId(Integer.parseInt(id))).collect(Collectors.toList()));
            });

            set.set("classRestriction", classRestriction);

            // Initial values in case handler doesn't exists
            set.set("handler", "");
            set.set("params", StatsSet.EMPTY_STATSET);

            // Parse handler and parameters
            forEach(missionNode, "handler", handlerNode -> {
                set.set("handler", parseString(handlerNode.getAttributes(), "name"));

                final StatsSet params = new StatsSet();
                set.set("params", params);
                forEach(handlerNode, "param", paramNode -> params.set(parseString(paramNode.getAttributes(), "name"), paramNode.getTextContent()));
            });

            final MissionDataHolder holder = new MissionDataHolder(set);
            dailyMissionRewards.computeIfAbsent(holder.getId(), k -> new ArrayList<>()).add(holder);
        }));
    }

    public Collection<MissionDataHolder> getDailyMissions() {
        //@formatter:off
        return dailyMissionRewards.values()
                .stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        //@formatter:on
    }

    public Collection<MissionDataHolder> getDailyMissions(Player player) {
        //@formatter:off
        return dailyMissionRewards.values()
                .stream()
                .flatMap(List::stream)
                .filter(o -> o.isDisplayable(player))
                .collect(Collectors.toList());
        //@formatter:on
    }

    public int getAvailableDailyMissionCount(Player player) {
        return (int) dailyMissionRewards.values().stream().flatMap(List::stream).filter(mission -> mission.isAvailable(player)).count();
    }

    public Collection<MissionDataHolder> getDailyMissions(int id) {
        return dailyMissionRewards.get(id);
    }

    public void clearMissionData(int id) {
        missionsData.values().forEach(map -> map.remove(id));
    }

    public void storeMissionData(int missionId, MissionPlayerData data) {
        if(nonNull(data)) {
            missionsData.computeIfAbsent(data.getObjectId(), id -> new CHashIntMap<>()).putIfAbsent(missionId, data);
        }
    }

    public IntMap<MissionPlayerData> getStoredDailyMissionData(Player player) {
        return missionsData.computeIfAbsent(player.getObjectId(), id -> new CHashIntMap<>());
    }

    public boolean isAvailable() {
        return available;
    }

    public static void init() {
        getInstance().load();
    }

    public static MissionData getInstance() {
        return Singleton.INSTANCE;
    }

    private static class Singleton {
        private static final MissionData INSTANCE = new MissionData();
    }
}
