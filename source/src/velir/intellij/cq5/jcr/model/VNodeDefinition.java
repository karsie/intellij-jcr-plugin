package velir.intellij.cq5.jcr.model;

import com.intellij.openapi.diagnostic.Logger;
import velir.intellij.cq5.jcr.Connection;

import javax.jcr.*;
import java.util.*;

public class VNodeDefinition {
	public static final String JCR_ISMIXIN = "jcr:isMixin";
	public static final String JCR_NAME = "jcr:name";
	public static final String JCR_NODETYPENAME = "jcr:nodeTypeName";
	public static final String JCR_ONPARENTVERSION = "jcr:onParentVersion";
	public static final String JCR_SUPERTYPES = "jcr:supertypes";
	public static final String NT_PROPERTYDEFINITION = "nt:propertyDefinition";

	private static final Logger log = com.intellij.openapi.diagnostic.Logger.getInstance(VNodeDefinition.class);

	private static Map<String,VNodeDefinition> allNodes;

	private Map<String,VPropertyDefinition> properties;
	private Set<String> parents;
	private boolean canAddProperties;
	private boolean isMixin;
	private String name;

	public VNodeDefinition (Node node) throws RepositoryException {
		name = node.getProperty(JCR_NODETYPENAME).getString();

		// do properties
		properties = new HashMap<String, VPropertyDefinition>();
		NodeIterator nodeIterator = node.getNodes();
		while (nodeIterator.hasNext()) {
			Node propertyNode = nodeIterator.nextNode();

			// do a property
			if (NT_PROPERTYDEFINITION.equals(propertyNode.getProperty(VNode.JCR_PRIMARYTYPE).getString())) {
				String propertyName = "*"; // default to wildcard name
				if (propertyNode.hasProperty(JCR_NAME)) {

					// only add non-computed properties
					if (! "COMPUTED".equals(propertyNode.getProperty(JCR_ONPARENTVERSION).getString())) {
						propertyName = propertyNode.getProperty(JCR_NAME).getString();
						properties.put(propertyName, new VPropertyDefinition(propertyNode));
					}
				} else {
					// property with no name means this node can accept custom properties
					canAddProperties = true;
				}
			}
		}

		// do parents
		parents = new HashSet<String>();
		if (node.hasProperty(JCR_SUPERTYPES)) {
			for (Value value : node.getProperty(JCR_SUPERTYPES).getValues()) {
				parents.add(value.getString());
			}
		}

		// set mixin status
		isMixin = node.hasProperty(JCR_ISMIXIN) && node.getProperty(JCR_ISMIXIN).getBoolean();
	}

	public Map<String, Object> getPropertiesMap (boolean includePrimaryType) {
		Map<String,Object> propertiesMap = new HashMap<String, Object>();
		for (Map.Entry<String, VPropertyDefinition> entry : properties.entrySet()) {
			propertiesMap.put(entry.getKey(), entry.getValue().getDefaultValue());
		}
		if (includePrimaryType) propertiesMap.put(VNode.JCR_PRIMARYTYPE, name);

		// also get supertype properties
		for (String parentName : parents) {
			VNodeDefinition vNodeDefinition = VNodeDefinition.getDefinition(parentName);
			if (vNodeDefinition != null) propertiesMap.putAll(vNodeDefinition.getPropertiesMap(false));
			else {
				log.error("Could not get definition for " + parentName);
			}
		}

		return propertiesMap;
	}

	public static void buildDefinitions () {
		log.info("started building node definitions");
		Session session = null;
		String nodeName = "";
		try {
			allNodes = new HashMap<String, VNodeDefinition>();
			session = Connection.getSession();
			Node rootNode = session.getNode("/jcr:system/jcr:nodeTypes");
			NodeIterator nodeIterator = rootNode.getNodes();
			while (nodeIterator.hasNext()) {
				Node node = nodeIterator.nextNode();
				nodeName = node.getName();
				allNodes.put(nodeName, new VNodeDefinition(node));
			}
			log.info("finished building nodes");
		} catch (RepositoryException re) {
			log.error("Could not build node definitions, died at " + nodeName, re);
		} finally {
			if (session != null) session.logout();
		}
	}

	public static boolean hasDefinitions () {
		return ! allNodes.isEmpty();
	}

	// only include non-mixin types
	public static String[] getNodeTypeNames () {
		// filter mixins out
		Set<String> keySet = allNodes.keySet();
		Set<String> copySet = new HashSet<String>();
		for (String s : keySet) copySet.add(s); // do I really need to do this, java?
		for (String s : keySet) {
			if (getDefinition(s).isMixin) copySet.remove(s);
		}

		String[] options = new String[copySet.size()];
		options = copySet.toArray(options);
		Arrays.sort(options);
		return options;
	}

	public static VNodeDefinition getDefinition (String name) {
		return allNodes.get(name);
	}


}
