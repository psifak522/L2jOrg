package org.l2j.gameserver.model.skills;

import org.l2j.commons.threading.ThreadPoolManager;
import org.l2j.gameserver.data.xml.impl.SkillData;
import org.l2j.gameserver.enums.ShotType;
import org.l2j.gameserver.geoengine.GeoEngine;
import org.l2j.gameserver.model.WorldObject;
import org.l2j.gameserver.model.actor.Creature;
import org.l2j.gameserver.model.actor.instance.Player;
import org.l2j.gameserver.network.SystemMessageId;
import org.l2j.gameserver.network.serverpackets.MagicSkillLaunched;
import org.l2j.gameserver.util.GameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.l2j.gameserver.util.GameUtils.isPlayer;


/**
 * Skill Channelizer implementation.
 *
 * @author UnAfraid
 */
public class SkillChannelizer implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkillChannelizer.class);

    private final Creature _channelizer;
    private List<Creature> _channelized;

    private Skill _skill;
    private volatile ScheduledFuture<?> _task = null;

    public SkillChannelizer(Creature channelizer) {
        _channelizer = channelizer;
    }

    public Creature getChannelizer() {
        return _channelizer;
    }

    public List<Creature> getChannelized() {
        return _channelized;
    }

    public boolean hasChannelized() {
        return _channelized != null;
    }

    public void startChanneling(Skill skill) {
        // Verify for same status.
        if (isChanneling()) {
            LOGGER.warn("Character: " + toString() + " is attempting to channel skill but he already does!");
            return;
        }

        // Start channeling.
        _skill = skill;
        _task = ThreadPoolManager.getInstance().scheduleAtFixedRate(this, skill.getChannelingTickInitialDelay(), skill.getChannelingTickInterval());
    }

    public void stopChanneling() {
        // Verify for same status.
        if (!isChanneling()) {
            LOGGER.warn("Character: " + toString() + " is attempting to stop channel skill but he does not!");
            return;
        }

        // Cancel the task and unset it.
        _task.cancel(false);
        _task = null;

        // Cancel target channelization and unset it.
        if (_channelized != null) {
            for (Creature chars : _channelized) {
                chars.getSkillChannelized().removeChannelizer(_skill.getChannelingSkillId(), _channelizer);
            }
            _channelized = null;
        }

        // unset skill.
        _skill = null;
    }

    public Skill getSkill() {
        return _skill;
    }

    public boolean isChanneling() {
        return _task != null;
    }

    @Override
    public void run() {
        if (!isChanneling()) {
            return;
        }

        final Skill skill = _skill;
        List<Creature> channelized = _channelized;

        try {
            if (skill.getMpPerChanneling() > 0) {
                // Validate mana per tick.
                if (_channelizer.getCurrentMp() < skill.getMpPerChanneling()) {
                    if (isPlayer(_channelizer)) {
                        _channelizer.sendPacket(SystemMessageId.YOUR_SKILL_WAS_DEACTIVATED_DUE_TO_LACK_OF_MP);
                    }
                    _channelizer.abortCast();
                    return;
                }

                // Reduce mana per tick
                _channelizer.reduceCurrentMp(skill.getMpPerChanneling());
            }

            // Apply channeling skills on the targets.
            final List<Creature> targetList = new ArrayList<>();
            final WorldObject target = skill.getTarget(_channelizer, false, false, false);
            if (target != null) {
                skill.forEachTargetAffected(_channelizer, target, o ->
                {
                    if (o.isCharacter()) {
                        targetList.add((Creature) o);
                        ((Creature) o).getSkillChannelized().addChannelizer(skill.getChannelingSkillId(), _channelizer);
                    }
                });
            }

            if (targetList.isEmpty()) {
                return;
            }
            channelized = targetList;

            for (Creature character : channelized) {
                if (!GameUtils.checkIfInRange(skill.getEffectRange(), _channelizer, character, true)) {
                    continue;
                } else if (!GeoEngine.getInstance().canSeeTarget(_channelizer, character)) {
                    continue;
                }

                if (skill.getChannelingSkillId() > 0) {
                    final int maxSkillLevel = SkillData.getInstance().getMaxLevel(skill.getChannelingSkillId());
                    final int skillLevel = Math.min(character.getSkillChannelized().getChannerlizersSize(skill.getChannelingSkillId()), maxSkillLevel);
                    final BuffInfo info = character.getEffectList().getBuffInfoBySkillId(skill.getChannelingSkillId());

                    if ((info == null) || (info.getSkill().getLevel() < skillLevel)) {
                        final Skill channeledSkill = SkillData.getInstance().getSkill(skill.getChannelingSkillId(), skillLevel);
                        if (channeledSkill == null) {
                            LOGGER.warn(": Non existent channeling skill requested: " + skill);
                            _channelizer.abortCast();
                            return;
                        }

                        // Update PvP status
                        if (isPlayer(_channelizer)) {
                            ((Player) _channelizer).updatePvPStatus(character);
                        }

                        // Be warned, this method has the possibility to call doDie->abortCast->stopChanneling method. Variable cache above try{} is used in this case to avoid NPEs.
                        channeledSkill.applyEffects(_channelizer, character);
                    }
                    if (!skill.isToggle()) {
                        _channelizer.broadcastPacket(new MagicSkillLaunched(_channelizer, skill.getId(), skill.getLevel(), SkillCastingType.NORMAL, character));
                    }
                } else {
                    skill.applyChannelingEffects(_channelizer, character);
                }

                // Reduce shots.
                if (skill.useSpiritShot()) {
                    _channelizer.unchargeShot(_channelizer.isChargedShot(ShotType.BLESSED_SPIRITSHOTS) ? ShotType.BLESSED_SPIRITSHOTS : ShotType.SPIRITSHOTS);
                } else {
                    _channelizer.unchargeShot(_channelizer.isChargedShot(ShotType.BLESSED_SOULSHOTS) ? ShotType.BLESSED_SOULSHOTS : ShotType.SOULSHOTS);
                }

                // Shots are re-charged every cast.
                _channelizer.rechargeShots(skill.useSoulShot(), skill.useSpiritShot(), false);
            }
        } catch (Exception e) {
            LOGGER.warn("Error while channelizing skill: " + skill + " channelizer: " + _channelizer + " channelized: " + channelized, e);
        }
    }
}
