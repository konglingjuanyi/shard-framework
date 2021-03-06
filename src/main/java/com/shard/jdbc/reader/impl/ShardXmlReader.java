package com.shard.jdbc.reader.impl;

import com.shard.jdbc.exception.InvalidShardConfException;
import com.shard.jdbc.shard.ShardProperty;
import com.shard.jdbc.shard.ShardType;
import com.shard.jdbc.reader.Reader;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * shard configuration reader
 * Created by shun on 2015-12-16 16:10.
 */
public class ShardXmlReader extends Reader {

    private static final String CLASS_ATTRIBUTE = "class";
    private static final String TYPE_ATTRIBUTE = "type";
    private static final String MATCH_ATTRIBUTE = "match";
    private static final String RANGE_ATTRIBUTE = "range";

    private static final String SHARD_NODE = "shard";
    private static final String MATCH_NODE = "match";
    private static final String RANGE_NODE = "range";

    private static final String RANGE_HASH_SPLITTER = "/";

    private static final List<String> existedShardClasses = new ArrayList<String>();

    @Override
    public <T> List<T> process(String path, Class<T> tClass) throws Exception{
        SAXBuilder saxBuilder = new SAXBuilder();

        List<T> shardProperties = new ArrayList<T>();
        try {
            Document doc = saxBuilder.build(new File(path));
            Element root = doc.getRootElement();
            for (Element ele: root.getChildren(SHARD_NODE)) {
                if (existedShardClasses.contains(ele.getAttributeValue(CLASS_ATTRIBUTE))) {
                    throw new InvalidShardConfException(String.format("duplicate class:[%s] in shard configuration",
                            ele.getAttributeValue(CLASS_ATTRIBUTE)));
                }

                ShardProperty shardProperty = new ShardProperty();
                shardProperty.setClazz(ele.getAttributeValue(CLASS_ATTRIBUTE));
                shardProperty.setType(ele.getAttributeValue(TYPE_ATTRIBUTE));

                List<ShardProperty.MatchInfo> matchInfoList = new ArrayList<ShardProperty.MatchInfo>();
                //if shard type is range-hash, it has another node range, so join range and match value with /
                if (shardProperty.getType().equals(ShardType.RANGE_HASH)) {
                    List<Element> rangeNodeList = ele.getChildren(RANGE_NODE);
                    for (Element rangeNode:rangeNodeList) {
                        for (Element match:rangeNode.getChildren(MATCH_NODE)) {
                            ShardProperty.MatchInfo matchInfo = new ShardProperty.MatchInfo(
                                    rangeNode.getAttributeValue(RANGE_ATTRIBUTE) + RANGE_HASH_SPLITTER + match.getAttributeValue(MATCH_ATTRIBUTE),
                                    match.getTextTrim());
                            matchInfoList.add(matchInfo);
                        }
                    }
                }
                else if (shardProperty.getType().equals(ShardType.NONE)) {
                    Element match = ele.getChild(MATCH_NODE);
                    if (match != null) {
                        ShardProperty.MatchInfo matchInfo = new ShardProperty.MatchInfo(null, match.getTextTrim());
                        matchInfoList.add(matchInfo);
                    }
                }
                else if (!shardProperty.getType().equals(ShardType.NONE)) {
                    for (Element match:ele.getChildren(MATCH_NODE)) {
                        ShardProperty.MatchInfo matchInfo = new ShardProperty.MatchInfo(
                                match.getAttributeValue(MATCH_ATTRIBUTE), match.getTextTrim());
                        matchInfoList.add(matchInfo);
                    }
                }

                //如果没有Match信息，则直接抛出异常，不进行处理
                if (matchInfoList.size() == 0) {
                    throw new InvalidShardConfException("shard node for class:[%s] seems to be wrong, may be without match node",
                            ele.getAttributeValue(CLASS_ATTRIBUTE));
                }

                //用于判断是否重复
                existedShardClasses.add(ele.getAttributeValue(CLASS_ATTRIBUTE));

                shardProperty.setMatchInfoList(matchInfoList);
                shardProperties.add((T)shardProperty);
            }
        } catch (JDOMException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return shardProperties;
    }

}
