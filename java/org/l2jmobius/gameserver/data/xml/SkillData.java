/*
 * This file is part of the L2J Mobius project.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.l2jmobius.gameserver.data.xml;

import java.io.File;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import org.l2jmobius.Config;
import org.l2jmobius.commons.util.IXmlReader;
import org.l2jmobius.gameserver.handler.EffectHandler;
import org.l2jmobius.gameserver.handler.SkillConditionHandler;
import org.l2jmobius.gameserver.model.StatSet;
import org.l2jmobius.gameserver.model.effects.AbstractEffect;
import org.l2jmobius.gameserver.model.skill.CommonSkill;
import org.l2jmobius.gameserver.model.skill.EffectScope;
import org.l2jmobius.gameserver.model.skill.ISkillCondition;
import org.l2jmobius.gameserver.model.skill.Skill;
import org.l2jmobius.gameserver.model.skill.SkillConditionScope;

import net.objecthunter.exp4j.ExpressionBuilder;

/**
 * Skill data parser.
 * @author NosBit
 */
public class SkillData implements IXmlReader
{
	private static final Logger LOGGER = Logger.getLogger(SkillData.class.getName());
	
	private final Map<Long, Skill> _skills = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> _skillsMaxLevel = new ConcurrentHashMap<>();
	
	private class NamedParamInfo
	{
		private final String _name;
		private final Integer _fromLevel;
		private final Integer _toLevel;
		private final Integer _fromSubLevel;
		private final Integer _toSubLevel;
		private final Map<Integer, Map<Integer, StatSet>> _info;
		
		public NamedParamInfo(String name, Integer fromLevel, Integer toLevel, Integer fromSubLevel, Integer toSubLevel, Map<Integer, Map<Integer, StatSet>> info)
		{
			_name = name;
			_fromLevel = fromLevel;
			_toLevel = toLevel;
			_fromSubLevel = fromSubLevel;
			_toSubLevel = toSubLevel;
			_info = info;
		}
		
		public String getName()
		{
			return _name;
		}
		
		public Integer getFromLevel()
		{
			return _fromLevel;
		}
		
		public Integer getToLevel()
		{
			return _toLevel;
		}
		
		public Integer getFromSubLevel()
		{
			return _fromSubLevel;
		}
		
		public Integer getToSubLevel()
		{
			return _toSubLevel;
		}
		
		public Map<Integer, Map<Integer, StatSet>> getInfo()
		{
			return _info;
		}
	}
	
	protected SkillData()
	{
		load();
	}
	
	/**
	 * Provides the skill hash
	 * @param skill The Skill to be hashed
	 * @return getSkillHashCode(skill.getId(), skill.getLevel())
	 */
	public static long getSkillHashCode(Skill skill)
	{
		return getSkillHashCode(skill.getId(), skill.getLevel(), skill.getSubLevel());
	}
	
	/**
	 * Centralized method for easier change of the hashing sys
	 * @param skillId The Skill Id
	 * @param skillLevel The Skill Level
	 * @return The Skill hash number
	 */
	public static long getSkillHashCode(int skillId, int skillLevel)
	{
		return getSkillHashCode(skillId, skillLevel, 0);
	}
	
	/**
	 * Centralized method for easier change of the hashing sys
	 * @param skillId The Skill Id
	 * @param skillLevel The Skill Level
	 * @param subSkillLevel The skill sub level
	 * @return The Skill hash number
	 */
	public static long getSkillHashCode(int skillId, int skillLevel, int subSkillLevel)
	{
		return (skillId * 4294967296L) + (subSkillLevel * 65536) + skillLevel;
	}
	
	public Skill getSkill(int skillId, int level)
	{
		return getSkill(skillId, level, 0);
	}
	
	public Skill getSkill(int skillId, int level, int subLevel)
	{
		final Skill result = _skills.get(getSkillHashCode(skillId, level, subLevel));
		if (result != null)
		{
			return result;
		}
		
		// skill/level not found, fix for transformation scripts
		final int maxLevel = getMaxLevel(skillId);
		// requested level too high
		if ((maxLevel > 0) && (level > maxLevel))
		{
			LOGGER.log(Level.WARNING, getClass().getSimpleName() + ": Call to unexisting skill level id: " + skillId + " requested level: " + level + " max level: " + maxLevel + ".");
			return _skills.get(getSkillHashCode(skillId, maxLevel));
		}
		
		LOGGER.warning(getClass().getSimpleName() + ": No skill info found for skill id " + skillId + " and skill level " + level);
		return null;
	}
	
	public int getMaxLevel(int skillId)
	{
		final Integer maxLevel = _skillsMaxLevel.get(skillId);
		return maxLevel != null ? maxLevel : 0;
	}
	
	/**
	 * @param addNoble
	 * @param hasCastle
	 * @return an array with siege skills. If addNoble == true, will add also Advanced headquarters.
	 */
	public List<Skill> getSiegeSkills(boolean addNoble, boolean hasCastle)
	{
		final List<Skill> temp = new LinkedList<>();
		temp.add(_skills.get(getSkillHashCode(CommonSkill.IMPRIT_OF_LIGHT.getId(), 1)));
		temp.add(_skills.get(getSkillHashCode(CommonSkill.IMPRIT_OF_DARKNESS.getId(), 1)));
		temp.add(_skills.get(getSkillHashCode(247, 1))); // Build Headquarters
		if (addNoble)
		{
			temp.add(_skills.get(getSkillHashCode(326, 1))); // Build Advanced Headquarters
		}
		if (hasCastle)
		{
			temp.add(_skills.get(getSkillHashCode(844, 1))); // Outpost Construction
			temp.add(_skills.get(getSkillHashCode(845, 1))); // Outpost Demolition
		}
		return temp;
	}
	
	@Override
	public boolean isValidating()
	{
		return false;
	}
	
	@Override
	public synchronized void load()
	{
		_skills.clear();
		_skillsMaxLevel.clear();
		parseDatapackDirectory("data/stats/skills/", false);
		if (Config.CUSTOM_SKILLS_LOAD)
		{
			parseDatapackDirectory("data/stats/skills/custom", false);
		}
		LOGGER.info(getClass().getSimpleName() + ": Loaded " + _skills.size() + " Skills.");
	}
	
	public void reload()
	{
		load();
		// Reload Skill Tree as well.
		SkillTreeData.getInstance().load();
	}
	
	@Override
	public void parseDocument(Document doc, File f)
	{
		for (Node node = doc.getFirstChild(); node != null; node = node.getNextSibling())
		{
			if ("list".equalsIgnoreCase(node.getNodeName()))
			{
				for (Node listNode = node.getFirstChild(); listNode != null; listNode = listNode.getNextSibling())
				{
					if ("skill".equalsIgnoreCase(listNode.getNodeName()))
					{
						NamedNodeMap attributes = listNode.getAttributes();
						final Map<Integer, Set<Integer>> levels = new HashMap<>();
						final Map<Integer, Map<Integer, StatSet>> skillInfo = new HashMap<>();
						final StatSet generalSkillInfo = skillInfo.computeIfAbsent(-1, k -> new HashMap<>()).computeIfAbsent(-1, k -> new StatSet());
						parseAttributes(attributes, "", generalSkillInfo);
						
						final Map<String, Map<Integer, Map<Integer, Object>>> variableValues = new HashMap<>();
						final Map<EffectScope, List<NamedParamInfo>> effectParamInfo = new EnumMap<>(EffectScope.class);
						final Map<SkillConditionScope, List<NamedParamInfo>> conditionParamInfo = new EnumMap<>(SkillConditionScope.class);
						for (Node skillNode = listNode.getFirstChild(); skillNode != null; skillNode = skillNode.getNextSibling())
						{
							final String skillNodeName = skillNode.getNodeName();
							switch (skillNodeName.toLowerCase())
							{
								case "variable":
								{
									attributes = skillNode.getAttributes();
									final String name = "@" + parseString(attributes, "name");
									variableValues.put(name, parseValues(skillNode));
									break;
								}
								case "#text":
								{
									break;
								}
								default:
								{
									final EffectScope effectScope = EffectScope.findByXmlNodeName(skillNodeName);
									if (effectScope != null)
									{
										for (Node effectsNode = skillNode.getFirstChild(); effectsNode != null; effectsNode = effectsNode.getNextSibling())
										{
											switch (effectsNode.getNodeName().toLowerCase())
											{
												case "effect":
												{
													effectParamInfo.computeIfAbsent(effectScope, k -> new LinkedList<>()).add(parseNamedParamInfo(effectsNode, variableValues));
													break;
												}
											}
										}
										break;
									}
									final SkillConditionScope skillConditionScope = SkillConditionScope.findByXmlNodeName(skillNodeName);
									if (skillConditionScope != null)
									{
										for (Node conditionNode = skillNode.getFirstChild(); conditionNode != null; conditionNode = conditionNode.getNextSibling())
										{
											switch (conditionNode.getNodeName().toLowerCase())
											{
												case "condition":
												{
													conditionParamInfo.computeIfAbsent(skillConditionScope, k -> new LinkedList<>()).add(parseNamedParamInfo(conditionNode, variableValues));
													break;
												}
											}
										}
									}
									else
									{
										parseInfo(skillNode, variableValues, skillInfo);
									}
									break;
								}
							}
						}
						
						final int fromLevel = generalSkillInfo.getInt(".fromLevel", 1);
						final int toLevel = generalSkillInfo.getInt(".toLevel", 0);
						for (int i = fromLevel; i <= toLevel; i++)
						{
							levels.computeIfAbsent(i, k -> new HashSet<>()).add(0);
						}
						
						skillInfo.forEach((level, subLevelMap) ->
						{
							if (level == -1)
							{
								return;
							}
							subLevelMap.forEach((subLevel, statSet) ->
							{
								if (subLevel == -1)
								{
									return;
								}
								levels.computeIfAbsent(level, k -> new HashSet<>()).add(subLevel);
							});
						});
						
						Stream.concat(effectParamInfo.values().stream(), conditionParamInfo.values().stream()).forEach(namedParamInfos -> namedParamInfos.forEach(namedParamInfo ->
						{
							namedParamInfo.getInfo().forEach((level, subLevelMap) ->
							{
								if (level == -1)
								{
									return;
								}
								subLevelMap.forEach((subLevel, statSet) ->
								{
									if (subLevel == -1)
									{
										return;
									}
									levels.computeIfAbsent(level, k -> new HashSet<>()).add(subLevel);
								});
							});
							
							if ((namedParamInfo.getFromLevel() != null) && (namedParamInfo.getToLevel() != null))
							{
								for (int i = namedParamInfo.getFromLevel(); i <= namedParamInfo.getToLevel(); i++)
								{
									if ((namedParamInfo.getFromSubLevel() != null) && (namedParamInfo.getToSubLevel() != null))
									{
										for (int j = namedParamInfo.getFromSubLevel(); j <= namedParamInfo.getToSubLevel(); j++)
										{
											levels.computeIfAbsent(i, k -> new HashSet<>()).add(j);
										}
									}
									else
									{
										levels.computeIfAbsent(i, k -> new HashSet<>()).add(0);
									}
								}
							}
						}));
						
						levels.forEach((level, subLevels) -> subLevels.forEach(subLevel ->
						{
							final StatSet statSet = Optional.ofNullable(skillInfo.getOrDefault(level, Collections.emptyMap()).get(subLevel)).orElseGet(StatSet::new);
							skillInfo.getOrDefault(level, Collections.emptyMap()).getOrDefault(-1, StatSet.EMPTY_STATSET).getSet().forEach(statSet.getSet()::putIfAbsent);
							skillInfo.getOrDefault(-1, Collections.emptyMap()).getOrDefault(-1, StatSet.EMPTY_STATSET).getSet().forEach(statSet.getSet()::putIfAbsent);
							statSet.set(".level", level);
							statSet.set(".subLevel", subLevel);
							final Skill skill = new Skill(statSet);
							forEachNamedParamInfoParam(effectParamInfo, level, subLevel, ((effectScope, params) ->
							{
								final String effectName = params.getString(".name");
								params.remove(".name");
								try
								{
									final Function<StatSet, AbstractEffect> effectFunction = EffectHandler.getInstance().getHandlerFactory(effectName);
									if (effectFunction != null)
									{
										skill.addEffect(effectScope, effectFunction.apply(params));
									}
									else
									{
										LOGGER.warning(getClass().getSimpleName() + ": Missing effect for Skill Id[" + statSet.getInt(".id") + "] Level[" + level + "] SubLevel[" + subLevel + "] Effect Scope[" + effectScope + "] Effect Name[" + effectName + "]");
									}
								}
								catch (Exception e)
								{
									LOGGER.log(Level.WARNING, getClass().getSimpleName() + ": Failed loading effect for Skill Id[" + statSet.getInt(".id") + "] Level[" + level + "] SubLevel[" + subLevel + "] Effect Scope[" + effectScope + "] Effect Name[" + effectName + "]", e);
								}
							}));
							
							forEachNamedParamInfoParam(conditionParamInfo, level, subLevel, ((skillConditionScope, params) ->
							{
								final String conditionName = params.getString(".name");
								params.remove(".name");
								try
								{
									final Function<StatSet, ISkillCondition> conditionFunction = SkillConditionHandler.getInstance().getHandlerFactory(conditionName);
									if (conditionFunction != null)
									{
										if (skill.isPassive())
										{
											if (skillConditionScope != SkillConditionScope.PASSIVE)
											{
												LOGGER.warning(getClass().getSimpleName() + ": Non passive condition for passive Skill Id[" + statSet.getInt(".id") + "] Level[" + level + "] SubLevel[" + subLevel + "]");
											}
										}
										else if (skillConditionScope == SkillConditionScope.PASSIVE)
										{
											LOGGER.warning(getClass().getSimpleName() + ": Passive condition for non passive Skill Id[" + statSet.getInt(".id") + "] Level[" + level + "] SubLevel[" + subLevel + "]");
										}
										
										skill.addCondition(skillConditionScope, conditionFunction.apply(params));
									}
									else
									{
										LOGGER.warning(getClass().getSimpleName() + ": Missing condition for Skill Id[" + statSet.getInt(".id") + "] Level[" + level + "] SubLevel[" + subLevel + "] Effect Scope[" + skillConditionScope + "] Effect Name[" + conditionName + "]");
									}
								}
								catch (Exception e)
								{
									LOGGER.log(Level.WARNING, getClass().getSimpleName() + ": Failed loading condition for Skill Id[" + statSet.getInt(".id") + "] Level[" + level + "] SubLevel[" + subLevel + "] Condition Scope[" + skillConditionScope + "] Condition Name[" + conditionName + "]", e);
								}
							}));
							
							_skills.put(getSkillHashCode(skill), skill);
							_skillsMaxLevel.merge(skill.getId(), skill.getLevel(), Integer::max);
							if ((skill.getSubLevel() % 1000) == 1)
							{
								EnchantSkillGroupsData.getInstance().addRouteForSkill(skill.getId(), skill.getLevel(), skill.getSubLevel());
							}
						}));
					}
				}
			}
		}
	}
	
	private <T> void forEachNamedParamInfoParam(Map<T, List<NamedParamInfo>> paramInfo, int level, int subLevel, BiConsumer<T, StatSet> consumer)
	{
		paramInfo.forEach((scope, namedParamInfos) -> namedParamInfos.forEach(namedParamInfo ->
		{
			if ((((namedParamInfo.getFromLevel() == null) && (namedParamInfo.getToLevel() == null)) || ((namedParamInfo.getFromLevel() <= level) && (namedParamInfo.getToLevel() >= level))) //
				&& (((namedParamInfo.getFromSubLevel() == null) && (namedParamInfo.getToSubLevel() == null)) || ((namedParamInfo.getFromSubLevel() <= subLevel) && (namedParamInfo.getToSubLevel() >= subLevel))))
			{
				final StatSet params = Optional.ofNullable(namedParamInfo.getInfo().getOrDefault(level, Collections.emptyMap()).get(subLevel)).orElseGet(StatSet::new);
				namedParamInfo.getInfo().getOrDefault(level, Collections.emptyMap()).getOrDefault(-1, StatSet.EMPTY_STATSET).getSet().forEach(params.getSet()::putIfAbsent);
				namedParamInfo.getInfo().getOrDefault(-1, Collections.emptyMap()).getOrDefault(-1, StatSet.EMPTY_STATSET).getSet().forEach(params.getSet()::putIfAbsent);
				params.set(".name", namedParamInfo.getName());
				consumer.accept(scope, params);
			}
		}));
	}
	
	private NamedParamInfo parseNamedParamInfo(Node node, Map<String, Map<Integer, Map<Integer, Object>>> variableValues)
	{
		Node n = node;
		final NamedNodeMap attributes = n.getAttributes();
		final String name = parseString(attributes, "name");
		final Integer level = parseInteger(attributes, "level");
		final Integer fromLevel = parseInteger(attributes, "fromLevel", level);
		final Integer toLevel = parseInteger(attributes, "toLevel", level);
		final Integer subLevel = parseInteger(attributes, "subLevel");
		final Integer fromSubLevel = parseInteger(attributes, "fromSubLevel", subLevel);
		final Integer toSubLevel = parseInteger(attributes, "toSubLevel", subLevel);
		final Map<Integer, Map<Integer, StatSet>> info = new HashMap<>();
		for (n = n.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if (!n.getNodeName().equals("#text"))
			{
				parseInfo(n, variableValues, info);
			}
		}
		return new NamedParamInfo(name, fromLevel, toLevel, fromSubLevel, toSubLevel, info);
	}
	
	private void parseInfo(Node node, Map<String, Map<Integer, Map<Integer, Object>>> variableValues, Map<Integer, Map<Integer, StatSet>> info)
	{
		Map<Integer, Map<Integer, Object>> values = parseValues(node);
		final Object generalValue = values.getOrDefault(-1, Collections.emptyMap()).get(-1);
		if (generalValue != null)
		{
			final String stringGeneralValue = String.valueOf(generalValue);
			if (stringGeneralValue.startsWith("@"))
			{
				final Map<Integer, Map<Integer, Object>> variableValue = variableValues.get(stringGeneralValue);
				if (variableValue != null)
				{
					values = variableValue;
				}
				else
				{
					throw new IllegalArgumentException("undefined variable " + stringGeneralValue);
				}
			}
		}
		
		values.forEach((level, subLevelMap) -> subLevelMap.forEach((subLevel, value) -> info.computeIfAbsent(level, k -> new HashMap<>()).computeIfAbsent(subLevel, k -> new StatSet()).set(node.getNodeName(), value)));
	}
	
	private Map<Integer, Map<Integer, Object>> parseValues(Node node)
	{
		Node n = node;
		final Map<Integer, Map<Integer, Object>> values = new HashMap<>();
		Object parsedValue = parseValue(n, true, false, Collections.emptyMap());
		if (parsedValue != null)
		{
			values.computeIfAbsent(-1, k -> new HashMap<>()).put(-1, parsedValue);
		}
		else
		{
			for (n = n.getFirstChild(); n != null; n = n.getNextSibling())
			{
				if (n.getNodeName().equalsIgnoreCase("value"))
				{
					final NamedNodeMap attributes = n.getAttributes();
					final Integer level = parseInteger(attributes, "level");
					if (level != null)
					{
						parsedValue = parseValue(n, false, false, Collections.emptyMap());
						if (parsedValue != null)
						{
							final Integer subLevel = parseInteger(attributes, "subLevel", -1);
							values.computeIfAbsent(level, k -> new HashMap<>()).put(subLevel, parsedValue);
						}
					}
					else
					{
						final int fromLevel = parseInteger(attributes, "fromLevel");
						final int toLevel = parseInteger(attributes, "toLevel");
						final int fromSubLevel = parseInteger(attributes, "fromSubLevel", -1);
						final int toSubLevel = parseInteger(attributes, "toSubLevel", -1);
						for (int i = fromLevel; i <= toLevel; i++)
						{
							for (int j = fromSubLevel; j <= toSubLevel; j++)
							{
								final Map<Integer, Object> subValues = values.computeIfAbsent(i, k -> new HashMap<>());
								final Map<String, Double> variables = new HashMap<>();
								variables.put("index", (i - fromLevel) + 1d);
								variables.put("subIndex", (j - fromSubLevel) + 1d);
								final Object base = values.getOrDefault(i, Collections.emptyMap()).get(-1);
								final String baseText = String.valueOf(base);
								if ((base != null) && !(base instanceof StatSet) && (!baseText.equalsIgnoreCase("true") && !baseText.equalsIgnoreCase("false")))
								{
									variables.put("base", Double.parseDouble(baseText));
								}
								parsedValue = parseValue(n, false, false, variables);
								if (parsedValue != null)
								{
									subValues.put(j, parsedValue);
								}
							}
						}
					}
				}
			}
		}
		return values;
	}
	
	Object parseValue(Node node, boolean blockValue, boolean parseAttributes, Map<String, Double> variables)
	{
		Node n = node;
		StatSet statSet = null;
		List<Object> list = null;
		Object text = null;
		if (parseAttributes && (!n.getNodeName().equals("value") || !blockValue) && (n.getAttributes().getLength() > 0))
		{
			statSet = new StatSet();
			parseAttributes(n.getAttributes(), "", statSet, variables);
		}
		for (n = n.getFirstChild(); n != null; n = n.getNextSibling())
		{
			final String nodeName = n.getNodeName();
			switch (n.getNodeName())
			{
				case "#text":
				{
					final String value = n.getNodeValue().trim();
					if (!value.isEmpty())
					{
						text = parseNodeValue(value, variables);
					}
					break;
				}
				case "item":
				{
					if (list == null)
					{
						list = new LinkedList<>();
					}
					
					final Object value = parseValue(n, false, true, variables);
					if (value != null)
					{
						list.add(value);
					}
					break;
				}
				case "value":
				{
					if (blockValue)
					{
						break;
					}
					// fallthrough
				}
				default:
				{
					final Object value = parseValue(n, false, true, variables);
					if (value != null)
					{
						if (statSet == null)
						{
							statSet = new StatSet();
						}
						
						statSet.set(nodeName, value);
					}
				}
			}
		}
		if (list != null)
		{
			if (text != null)
			{
				throw new IllegalArgumentException("Text and list in same node are not allowed. Node[" + n + "]");
			}
			if (statSet != null)
			{
				statSet.set(".", list);
			}
			else
			{
				return list;
			}
		}
		if (text != null)
		{
			if (list != null)
			{
				throw new IllegalArgumentException("Text and list in same node are not allowed. Node[" + n + "]");
			}
			if (statSet != null)
			{
				statSet.set(".", text);
			}
			else
			{
				return text;
			}
		}
		return statSet;
	}
	
	private void parseAttributes(NamedNodeMap attributes, String prefix, StatSet statSet, Map<String, Double> variables)
	{
		for (int i = 0; i < attributes.getLength(); i++)
		{
			final Node attributeNode = attributes.item(i);
			statSet.set(prefix + "." + attributeNode.getNodeName(), parseNodeValue(attributeNode.getNodeValue(), variables));
		}
	}
	
	private void parseAttributes(NamedNodeMap attributes, String prefix, StatSet statSet)
	{
		parseAttributes(attributes, prefix, statSet, Collections.emptyMap());
	}
	
	private Object parseNodeValue(String value, Map<String, Double> variables)
	{
		if (value.startsWith("{") && value.endsWith("}"))
		{
			return new ExpressionBuilder(value).variables(variables.keySet()).build().setVariables(variables).evaluate();
		}
		return value;
	}
	
	public static SkillData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final SkillData INSTANCE = new SkillData();
	}
}
