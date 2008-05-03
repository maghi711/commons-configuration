/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.configuration2;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.naming.InitialContext;
import javax.sql.DataSource;

import org.apache.commons.configuration2.beanutils.BeanDeclaration;
import org.apache.commons.configuration2.beanutils.BeanFactory;
import org.apache.commons.configuration2.beanutils.BeanHelper;
import org.apache.commons.configuration2.beanutils.DefaultBeanFactory;
import org.apache.commons.configuration2.beanutils.XMLBeanDeclaration;
import org.apache.commons.configuration2.expr.NodeList;
import org.apache.commons.configuration2.tree.ConfigurationNode;
import org.apache.commons.configuration2.tree.DefaultExpressionEngine;
import org.apache.commons.configuration2.tree.OverrideCombiner;
import org.apache.commons.configuration2.tree.UnionCombiner;

/**
 * <p>
 * A factory class that creates a composite configuration from an XML based
 * <em>configuration definition file</em>.
 * </p>
 * <p>
 * This class provides an easy and flexible means for loading multiple
 * configuration sources and combining the results into a single configuration
 * object. The sources to be loaded are defined in an XML document that can
 * contain certain tags representing the different supported configuration
 * classes. If such a tag is found, the corresponding <code>Configuration</code>
 * class is instantiated and initialized using the classes of the
 * <code>beanutils</code> package (namely
 * <code>{@link org.apache.commons.configuration2.beanutils.XMLBeanDeclaration XMLBeanDeclaration}</code>
 * will be used to extract the configuration's initialization parameters, which
 * allows for complex initialization szenarios).
 * </p>
 * <p>
 * It is also possible to add custom tags to the configuration definition file.
 * For this purpose register your own <code>ConfigurationProvider</code>
 * implementation for your tag using the <code>addConfigurationProvider()</code>
 * method. This provider will then be called when the corresponding custom tag
 * is detected. For the default configuration classes providers are already
 * registered.
 * </p>
 * <p>
 * The configuration definition file has the following basic structure:
 * </p>
 * <p>
 *
 * <pre>
 * &lt;configuration&gt;
 *   &lt;header&gt;
 *     &lt;!-- Optional meta information about the composite configuration --&gt;
 *   &lt;/header&gt;
 *   &lt;override&gt;
 *     &lt;!-- Declarations for override configurations --&gt;
 *   &lt;/override&gt;
 *   &lt;additional&gt;
 *     &lt;!-- Declarations for union configurations --&gt;
 *   &lt;/additional&gt;
 * &lt;/configuration&gt;
 * </pre>
 *
 * </p>
 * <p>
 * The name of the root element (here <code>configuration</code>) is
 * arbitrary. There are two sections (both of them are optional) for declaring
 * <em>override</em> and <em>additional</em> configurations. Configurations
 * in the former section are evaluated in the order of their declaration, and
 * properties of configurations declared earlier hide those of configurations
 * declared later. Configurations in the latter section are combined to a union
 * configuration, i.e. all of their properties are added to a large hierarchical
 * configuration. Configuration declarations that occur as direct children of
 * the root element are treated as override declarations.
 * </p>
 * <p>
 * Each configuration declaration consists of a tag whose name is associated
 * with a <code>ConfigurationProvider</code>. This can be one of the
 * predefined tags like <code>properties</code>, or <code>xml</code>, or
 * a custom tag, for which a configuration provider was registered. Attributes
 * and sub elements with specific initialization parameters can be added. There
 * are some reserved attributes with a special meaning that can be used in every
 * configuration declaration:
 * </p>
 * <p>
 * <table border="1">
 * <tr>
 * <th>Attribute</th>
 * <th>Meaning</th>
 * </tr>
 * <tr>
 * <td valign="top"><code>config-name</code></td>
 * <td>Allows to specify a name for this configuration. This name can be used
 * to obtain a reference to the configuration from the resulting combined
 * configuration (see below).</td>
 * </tr>
 * <tr>
 * <td valign="top"><code>config-at</code></td>
 * <td>With this attribute an optional prefix can be specified for the
 * properties of the corresponding configuration.</td>
 * </tr>
 * <tr>
 * <td valign="top"><code>config-optional</code></td>
 * <td>Declares a configuration as optional. This means that errors that occur
 * when creating the configuration are ignored. (However
 * <code>{@link org.apache.commons.configuration2.event.ConfigurationErrorListener}</code>s
 * registered at the builder instance will get notified about this error: they
 * receive an event of type <code>EVENT_ERR_LOAD_OPTIONAL</code>. The key
 * property of this event contains the name of the optional configuration source
 * that caused this problem.)</td>
 * </tr>
 * </table>
 * </p>
 * <p>
 * The optional <em>header</em> section can contain some meta data about the
 * created configuration itself. For instance, it is possible to set further
 * properties of the <code>NodeCombiner</code> objects used for constructing
 * the resulting configuration.
 * </p>
 * <p>
 * The configuration object returned by this builder is an instance of the
 * <code>{@link CombinedConfiguration}</code> class. The return value of the
 * <code>getConfiguration()</code> method can be casted to this type, and the
 * <code>getConfiguration(boolean)</code> method directly declares
 * <code>CombinedConfiguration</code> as return type. This allows for
 * convenient access to the configuration objects maintained by the combined
 * configuration (e.g. for updates of single configuration objects). It has also
 * the advantage that the properties stored in all declared configuration
 * objects are collected and transformed into a single hierarchical structure,
 * which can be accessed using different expression engines.
 * </p>
 * <p>
 * All declared override configurations are directly added to the resulting
 * combined configuration. If they are given names (using the
 * <code>config-name</code> attribute), they can directly be accessed using
 * the <code>getConfiguration(String)</code> method of
 * <code>CombinedConfiguration</code>. The additional configurations are
 * alltogether added to another combined configuration, which uses a union
 * combiner. Then this union configuration is added to the resulting combined
 * configuration under the name defined by the <code>ADDITIONAL_NAME</code>
 * constant.
 * </p>
 * <p>
 * Implementation note: This class is not thread-safe. Especially the
 * <code>getConfiguration()</code> methods should be called by a single thread
 * only.
 * </p>
 *
 * @since 1.3
 * @author <a
 * href="http://commons.apache.org/configuration/team-list.html">Commons
 * Configuration team</a>
 * @version $Id$
 */
public class DefaultConfigurationBuilder extends XMLConfiguration implements
        ConfigurationBuilder
{
    /**
     * Constant for the name of the additional configuration. If the
     * configuration definition file contains an <code>additional</code>
     * section, a special union configuration is created and added under this
     * name to the resulting combined configuration.
     */
    public static final String ADDITIONAL_NAME = DefaultConfigurationBuilder.class
            .getName()
            + "/ADDITIONAL_CONFIG";

    /**
     * Constant for the type of error events caused by optional configurations
     * that cannot be loaded.
     */
    public static final int EVENT_ERR_LOAD_OPTIONAL = 51;

    /** Constant for the name of the configuration bean factory. */
    static final String CONFIG_BEAN_FACTORY_NAME = DefaultConfigurationBuilder.class
            .getName()
            + ".CONFIG_BEAN_FACTORY_NAME";

    /** Constant for the reserved name attribute. */
    static final String ATTR_NAME = DefaultExpressionEngine.DEFAULT_ATTRIBUTE_START
            + XMLBeanDeclaration.RESERVED_PREFIX
            + "name"
            + DefaultExpressionEngine.DEFAULT_ATTRIBUTE_END;

    /** Constant for the name of the at attribute. */
    static final String ATTR_ATNAME = "at";

    /** Constant for the reserved at attribute. */
    static final String ATTR_AT_RES = DefaultExpressionEngine.DEFAULT_ATTRIBUTE_START
            + XMLBeanDeclaration.RESERVED_PREFIX
            + ATTR_ATNAME
            + DefaultExpressionEngine.DEFAULT_ATTRIBUTE_END;

    /** Constant for the at attribute without the reserved prefix. */
    static final String ATTR_AT = DefaultExpressionEngine.DEFAULT_ATTRIBUTE_START
            + ATTR_ATNAME + DefaultExpressionEngine.DEFAULT_ATTRIBUTE_END;

    /** Constant for the name of the optional attribute. */
    static final String ATTR_OPTIONALNAME = "optional";

    /** Constant for the reserved optional attribute. */
    static final String ATTR_OPTIONAL_RES = DefaultExpressionEngine.DEFAULT_ATTRIBUTE_START
            + XMLBeanDeclaration.RESERVED_PREFIX
            + ATTR_OPTIONALNAME
            + DefaultExpressionEngine.DEFAULT_ATTRIBUTE_END;

    /** Constant for the optional attribute without the reserved prefix. */
    static final String ATTR_OPTIONAL = DefaultExpressionEngine.DEFAULT_ATTRIBUTE_START
            + ATTR_OPTIONALNAME + DefaultExpressionEngine.DEFAULT_ATTRIBUTE_END;

    /** Constant for the file name attribute. */
    static final String ATTR_FILENAME = DefaultExpressionEngine.DEFAULT_ATTRIBUTE_START
            + "fileName" + DefaultExpressionEngine.DEFAULT_ATTRIBUTE_END;

    /** Constant for the forceCreate attribute. */
    static final String ATTR_FORCECREATE = DefaultExpressionEngine.DEFAULT_ATTRIBUTE_START
            + XMLBeanDeclaration.RESERVED_PREFIX
            + "forceCreate"
            + DefaultExpressionEngine.DEFAULT_ATTRIBUTE_END;

    /** Constant for the name of the header section. */
    static final String SEC_HEADER = "header";

    /** Constant for an expression that selects the union configurations. */
    static final String KEY_UNION = "additional";

    /** An array with the names of top level configuration sections.*/
    static final String[] CONFIG_SECTIONS = {
        "additional", "override", SEC_HEADER
    };

    /**
     * Constant for an expression that selects override configurations in the
     * override section.
     */
    static final String KEY_OVERRIDE = "override";

    /**
     * Constant for the key that points to the list nodes definition of the
     * override combiner.
     */
    static final String KEY_OVERRIDE_LIST = SEC_HEADER
            + ".combiner.override.list-nodes.node";

    /**
     * Constant for the key that points to the list nodes definition of the
     * additional combiner.
     */
    static final String KEY_ADDITIONAL_LIST = SEC_HEADER
            + ".combiner.additional.list-nodes.node";

    /**
     * Constant for the key of the result declaration. This key can point to a
     * bean declaration, which defines properties of the resulting combined
     * configuration.
     */
    static final String KEY_RESULT = SEC_HEADER + ".result";

    /** Constant for the key of the combiner in the result declaration.*/
    static final String KEY_COMBINER = KEY_RESULT + ".nodeCombiner";

    /** Constant for the XML file extension. */
    static final String EXT_XML = ".xml";

    /** Constant for the provider for properties files. */
    private static final ConfigurationProvider PROPERTIES_PROVIDER = new FileExtensionConfigurationProvider(
            XMLPropertiesConfiguration.class, PropertiesConfiguration.class,
            EXT_XML);

    /** Constant for the provider for XML files. */
    private static final ConfigurationProvider XML_PROVIDER = new XMLConfigurationProvider();

    /** Constant for the provider for JNDI sources. */
    private static final ConfigurationProvider JNDI_PROVIDER =
            new ConfigurationProvider(JNDIConfiguration.class);

    /** Constant for the provider for system properties. */
    private static final ConfigurationProvider SYSTEM_PROVIDER =
            new ConfigurationProvider(SystemConfiguration.class);

    /** Constant for the provider for environment variables. */
    private static final ConfigurationProvider ENVIRONMENT_PROVIDER =
            new ConfigurationProvider(EnvironmentConfiguration.class);

    /** Constant for the provider for environment variables. */
    private static final ConfigurationProvider DATABASE_PROVIDER =
            new DatabaseConfigurationProvider();

    /** Constant for the provider for plist files. */
    private static final ConfigurationProvider PLIST_PROVIDER = new FileExtensionConfigurationProvider(
            "org.apache.commons.configuration2.plist.XMLPropertyListConfiguration",
            "org.apache.commons.configuration2.plist.PropertyListConfiguration",
            EXT_XML);

    /** Constant for the provider for configuration definition files.*/
    private static final ConfigurationProvider BUILDER_PROVIDER = new ConfigurationBuilderProvider();

    /** An array with the names of the default tags. */
    private static final String[] DEFAULT_TAGS =
    {"properties", "xml", "hierarchicalXml", "jndi", "system", "environment", "plist", "configuration", "database"};

    /** An array with the providers for the default tags. */
    private static final ConfigurationProvider[] DEFAULT_PROVIDERS =
    {PROPERTIES_PROVIDER, XML_PROVIDER, XML_PROVIDER, JNDI_PROVIDER,
            SYSTEM_PROVIDER, ENVIRONMENT_PROVIDER, PLIST_PROVIDER, BUILDER_PROVIDER, DATABASE_PROVIDER};

    /**
     * The serial version UID.
     */
    private static final long serialVersionUID = -3113777854714492123L;

    /** Stores the configuration that is currently constructed.*/
    private CombinedConfiguration constructedConfiguration;

    /** Stores a map with the registered configuration providers. */
    private Map<String, ConfigurationProvider> providers;

    /** Stores the base path to the configuration sources to load. */
    private String configurationBasePath;

    /**
     * Creates a new instance of <code>DefaultConfigurationBuilder</code>. A
     * configuration definition file is not yet loaded. Use the diverse setter
     * methods provided by file based configurations to specify the
     * configuration definition file.
     */
    public DefaultConfigurationBuilder()
    {
        super();
        providers = new HashMap<String, ConfigurationProvider>();
        registerDefaultProviders();
        registerBeanFactory();
        setLogger(Logger.getLogger(getClass().getName()));
        addErrorLogListener();  // log errors per default
    }

    /**
     * Creates a new instance of <code>DefaultConfigurationBuilder</code> and
     * sets the specified configuration definition file.
     *
     * @param file the configuration definition file
     */
    public DefaultConfigurationBuilder(File file)
    {
        this();
        setFile(file);
    }

    /**
     * Creates a new instance of <code>DefaultConfigurationBuilder</code> and
     * sets the specified configuration definition file.
     *
     * @param fileName the name of the configuration definition file
     * @throws ConfigurationException if an error occurs when the file is loaded
     */
    public DefaultConfigurationBuilder(String fileName)
            throws ConfigurationException
    {
        this();
        setFileName(fileName);
    }

    /**
     * Creates a new instance of <code>DefaultConfigurationBuilder</code> and
     * sets the specified configuration definition file.
     *
     * @param url the URL to the configuration definition file
     * @throws ConfigurationException if an error occurs when the file is loaded
     */
    public DefaultConfigurationBuilder(URL url) throws ConfigurationException
    {
        this();
        setURL(url);
    }

    /**
     * Returns the base path for the configuration sources to load. This path is
     * used to resolve relative paths in the configuration definition file.
     *
     * @return the base path for configuration sources
     */
    public String getConfigurationBasePath()
    {
        return (configurationBasePath != null) ? configurationBasePath
                : getBasePath();
    }

    /**
     * Sets the base path for the configuration sources to load. Normally a base
     * path need not to be set because it is determined by the location of the
     * configuration definition file to load. All relative pathes in this file
     * are resolved relative to this file. Setting a base path makes sense if
     * such relative pathes should be otherwise resolved, e.g. if the
     * configuration file is loaded from the class path and all sub
     * configurations it refers to are stored in a special config directory.
     *
     * @param configurationBasePath the new base path to set
     */
    public void setConfigurationBasePath(String configurationBasePath)
    {
        this.configurationBasePath = configurationBasePath;
    }

    /**
     * Adds a configuration provider for the specified tag. Whenever this tag is
     * encountered in the configuration definition file this provider will be
     * called to create the configuration object.
     *
     * @param tagName the name of the tag in the configuration definition file
     * @param provider the provider for this tag
     */
    public void addConfigurationProvider(String tagName, ConfigurationProvider provider)
    {
        if (tagName == null)
        {
            throw new IllegalArgumentException("Tag name must not be null!");
        }
        if (provider == null)
        {
            throw new IllegalArgumentException("Provider must not be null!");
        }

        providers.put(tagName, provider);
    }

    /**
     * Removes the configuration provider for the specified tag name.
     *
     * @param tagName the tag name
     * @return the removed configuration provider or <b>null</b> if none was
     * registered for that tag
     */
    public ConfigurationProvider removeConfigurationProvider(String tagName)
    {
        return (ConfigurationProvider) providers.remove(tagName);
    }

    /**
     * Returns the configuration provider for the given tag.
     *
     * @param tagName the name of the tag
     * @return the provider that was registered for this tag or <b>null</b> if
     * there is none
     */
    public ConfigurationProvider providerForTag(String tagName)
    {
        return (ConfigurationProvider) providers.get(tagName);
    }

    /**
     * Returns the configuration provided by this builder. Loads and parses the
     * configuration definition file and creates instances for the declared
     * configurations.
     *
     * @return the configuration
     * @throws ConfigurationException if an error occurs
     */
    public Configuration getConfiguration() throws ConfigurationException
    {
        return getConfiguration(true);
    }

    /**
     * Returns the configuration provided by this builder. If the boolean
     * parameter is <b>true</b>, the configuration definition file will be
     * loaded. It will then be parsed, and instances for the declared
     * configurations will be created.
     *
     * @param load a flag whether the configuration definition file should be
     * loaded; a value of <b>false</b> would make sense if the file has already
     * been created or its content was manipulated using some of the property
     * accessor methods
     * @return the configuration
     * @throws ConfigurationException if an error occurs
     */
    public CombinedConfiguration getConfiguration(boolean load)
            throws ConfigurationException
    {
        if (load)
        {
            load();
        }

        CombinedConfiguration result = createResultConfiguration();
        constructedConfiguration = result;

        List<SubConfiguration<ConfigurationNode>> overrides = fetchTopLevelOverrideConfigs();
        overrides.addAll(fetchChildConfigs(KEY_OVERRIDE));
        initCombinedConfiguration(result, overrides, KEY_OVERRIDE_LIST);

        List<SubConfiguration<ConfigurationNode>> additionals = fetchChildConfigs(KEY_UNION);
        if (!additionals.isEmpty())
        {
            CombinedConfiguration addConfig = new CombinedConfiguration(new UnionCombiner());
            result.addConfiguration(addConfig, ADDITIONAL_NAME);
            initCombinedConfiguration(addConfig, additionals, KEY_ADDITIONAL_LIST);
        }

        return result;
    }

    /**
     * Creates the resulting combined configuration. This method is called by
     * <code>getConfiguration()</code>. It checks whether the
     * <code>header</code> section of the configuration definition file
     * contains a <code>result</code> element. If this is the case, it will be
     * used to initialize the properties of the newly created configuration
     * object.
     *
     * @return the resulting configuration object
     * @throws ConfigurationException if an error occurs
     */
    protected CombinedConfiguration createResultConfiguration()
            throws ConfigurationException
    {
        XMLBeanDeclaration decl = new XMLBeanDeclaration(this, KEY_RESULT, true);
        CombinedConfiguration result = (CombinedConfiguration) BeanHelper
                .createBean(decl, CombinedConfiguration.class);

        if (getMaxIndex(KEY_COMBINER) < 0)
        {
            // No combiner defined => set default
            result.setNodeCombiner(new OverrideCombiner());
        }

        return result;
    }

    /**
     * Initializes a combined configuration for the configurations of a specific
     * section. This method is called for the override and for the additional
     * section (if it exists).
     *
     * @param config the configuration to be initialized
     * @param containedConfigs the list with the declarations of the contained
     * configurations
     * @param keyListNodes a list with the declaration of list nodes
     * @throws ConfigurationException if an error occurs
     */
    protected void initCombinedConfiguration(
            CombinedConfiguration config,
            List<? extends AbstractHierarchicalConfiguration<ConfigurationNode>> containedConfigs,
            String keyListNodes) throws ConfigurationException
    {
        List<?> listNodes = getList(keyListNodes);
        for (Iterator<?> it = listNodes.iterator(); it.hasNext();)
        {
            config.getNodeCombiner().addListNode((String) it.next());
        }

        for (AbstractHierarchicalConfiguration<ConfigurationNode> conf : containedConfigs)
        {
            ConfigurationDeclaration decl = new ConfigurationDeclaration(this, conf);
            AbstractConfiguration newConf = createConfigurationAt(decl);
            if (newConf != null)
            {
                config.addConfiguration(newConf, decl.getConfiguration()
                        .getString(ATTR_NAME), decl.getAt());
            }
        }
    }

    /**
     * Registers the default configuration providers supported by this class.
     * This method will be called during initialization. It registers
     * configuration providers for the tags that are supported by default.
     */
    protected void registerDefaultProviders()
    {
        for (int i = 0; i < DEFAULT_TAGS.length; i++)
        {
            addConfigurationProvider(DEFAULT_TAGS[i], DEFAULT_PROVIDERS[i]);
        }
    }

    /**
     * Performs interpolation. This method will not only take this configuration
     * instance into account (which is the one that loaded the configuration
     * definition file), but also the so far constructed combined configuration.
     * So variables can be used that point to properties that are defined in
     * configuration sources loaded by this builder.
     *
     * @param value the value to be interpolated
     * @return the interpolated value
     */
    protected Object interpolate(Object value)
    {
        Object result = super.interpolate(value);
        if (constructedConfiguration != null)
        {
            result = constructedConfiguration.interpolate(result);
        }
        return result;
    }

    /**
     * Creates a configuration object from the specified configuration
     * declaration.
     *
     * @param decl the configuration declaration
     * @return the new configuration object
     * @throws ConfigurationException if an error occurs
     */
    private AbstractConfiguration createConfigurationAt(
            ConfigurationDeclaration decl) throws ConfigurationException
    {
        try
        {
            return (AbstractConfiguration) BeanHelper.createBean(decl);
        }
        catch (Exception ex)
        {
            // redirect to configuration exceptions
            throw new ConfigurationException(ex);
        }
    }

    /**
     * Returns a list with <code>SubConfiguration</code> objects for the
     * child nodes of the specified configuration node.
     *
     * @param node the start node
     * @return a list with subnode configurations for the node's children
     */
    private List<SubConfiguration<ConfigurationNode>> fetchChildConfigs(ConfigurationNode node)
    {
        List<ConfigurationNode> children = node.getChildren();
        List<SubConfiguration<ConfigurationNode>> result = 
            new ArrayList<SubConfiguration<ConfigurationNode>>(children.size());
        for (ConfigurationNode child : children)
        {
            result.add(createSubnodeConfiguration(child));
        }
        return result;
    }

    /**
     * Returns a list with <code>SubConfiguration</code> objects for the
     * child nodes of the node specified by the given key.
     *
     * @param key the key (must define exactly one node)
     * @return a list with subnode configurations for the node's children
     */
    private List<SubConfiguration<ConfigurationNode>> fetchChildConfigs(String key)
    {
        NodeList<ConfigurationNode> nodes = fetchNodeList(key);
        if (nodes.size() > 0)
        {
            return fetchChildConfigs(nodes.getNode(0));
        }
        else
        {
            return Collections.emptyList();
        }
    }

    /**
     * Finds the override configurations that are defined as top level elements
     * in the configuration definition file. This method will fetch the child
     * elements of the root node and remove the nodes that represent other
     * configuration sections. The remaining nodes are treated as definitions
     * for override configurations.
     *
     * @return a list with sub configurations for the top level override
     * configurations
     */
    private List<SubConfiguration<ConfigurationNode>> fetchTopLevelOverrideConfigs()
    {
        List<SubConfiguration<ConfigurationNode>> configs = fetchChildConfigs(getRootNode());
        Iterator<SubConfiguration<ConfigurationNode>> it = configs.iterator();
        while (it.hasNext())
        {
            String nodeName = it.next().getRootNode().getName();
            for (int i = 0; i < CONFIG_SECTIONS.length; i++)
            {
                if (CONFIG_SECTIONS[i].equals(nodeName))
                {
                    it.remove();
                    break;
                }
            }
        }
        return configs;
    }

    /**
     * Registers the bean factory used by this class if necessary. This method
     * is called by the constructor to ensure that the required bean factory is
     * available.
     */
    private void registerBeanFactory()
    {
        synchronized (getClass())
        {
            if (!BeanHelper.registeredFactoryNames().contains(
                    CONFIG_BEAN_FACTORY_NAME))
            {
                BeanHelper.registerBeanFactory(CONFIG_BEAN_FACTORY_NAME,
                        new ConfigurationBeanFactory());
            }
        }
    }

    /**
     * <p>
     * A base class for creating and initializing configuration sources.
     * </p>
     * <p>
     * Concrete sub classes of this base class are responsible for creating
     * specific <code>Configuration</code> objects for the tags in the
     * configuration definition file. The configuration factory will parse the
     * definition file and try to find a matching
     * <code>ConfigurationProvider</code> for each encountered tag. This
     * provider is then asked to create a corresponding
     * <code>Configuration</code> object. It is up to a concrete
     * implementation how this object is created and initialized.
     * </p>
     * <p>
     * Note that at the moment only configuration classes derived from
     * <code>{@link AbstractConfiguration}</code> are supported.
     * </p>
     */
    public static class ConfigurationProvider extends DefaultBeanFactory
    {
        /** Stores the class of the configuration to be created. */
        private Class configurationClass;

        /** Stores the name of the configuration class to be created.*/
        private String configurationClassName;

        /**
         * Creates a new uninitialized instance of
         * <code>ConfigurationProvider</code>.
         */
        public ConfigurationProvider()
        {
            this((Class) null);
        }

        /**
         * Creates a new instance of <code>ConfigurationProvider</code> and
         * sets the class of the configuration created by this provider.
         *
         * @param configClass the configuration class
         */
        public ConfigurationProvider(Class configClass)
        {
            setConfigurationClass(configClass);
        }

        /**
         * Creates a new instance of <code>ConfigurationProvider</code> and
         * sets the name of the class of the configuration created by this
         * provider.
         *
         * @param configClassName the name of the configuration class
         * @since 1.4
         */
        public ConfigurationProvider(String configClassName)
        {
            setConfigurationClassName(configClassName);
        }

        /**
         * Returns the class of the configuration returned by this provider.
         *
         * @return the class of the provided configuration
         */
        public Class getConfigurationClass()
        {
            return configurationClass;
        }

        /**
         * Sets the class of the configuration returned by this provider.
         *
         * @param configurationClass the configuration class
         */
        public void setConfigurationClass(Class configurationClass)
        {
            this.configurationClass = configurationClass;
        }

        /**
         * Returns the name of the configuration class returned by this
         * provider.
         *
         * @return the configuration class name
         * @since 1.4
         */
        public String getConfigurationClassName()
        {
            return configurationClassName;
        }

        /**
         * Sets the name of the configuration class returned by this provider.
         *
         * @param configurationClassName the name of the configuration class
         * @since 1.4
         */
        public void setConfigurationClassName(String configurationClassName)
        {
            this.configurationClassName = configurationClassName;
        }

        /**
         * Returns the configuration. This method is called to fetch the
         * configuration from the provider. This implementation will call the
         * inherited <code>{@link
         * org.apache.commons.configuration2.beanutils.DefaultBeanFactory#createBean(Class, BeanDeclaration, Object)
         * createBean()}</code> method to create a new instance of the
         * configuration class.
         *
         * @param decl the bean declaration with initialization parameters for
         * the configuration
         * @return the new configuration object
         * @throws Exception if an error occurs
         */
        public AbstractConfiguration getConfiguration(
                ConfigurationDeclaration decl) throws Exception
        {
            return (AbstractConfiguration) createBean(fetchConfigurationClass(),
                    decl, null);
        }

        /**
         * Returns an uninitialized configuration of the represented type. This
         * method will be called for optional configurations when the
         * <code>getConfiguration()</code> method caused an error and the
         * <code>forceCreate</code> attribute is set. A concrete sub class can
         * here try to create an uninitialized, empty configuration, which may
         * be possible if the error was created during initialization. This base
         * implementation just returns <b>null</b>.
         *
         * @param decl the bean declaration with initialization parameters for
         * the configuration
         * @return the new configuration object
         * @throws Exception if an error occurs
         * @since 1.4
         */
        public AbstractConfiguration getEmptyConfiguration(
                ConfigurationDeclaration decl) throws Exception
        {
            return null;
        }

        /**
         * Returns the configuration class supported by this provider. If a
         * class object was set, it is returned. Otherwise the method tries to
         * resolve the class name.
         *
         * @return the class of the configuration to be created
         * @since 1.4
         */
        protected synchronized Class fetchConfigurationClass() throws Exception
        {
            if (getConfigurationClass() == null)
            {
                setConfigurationClass(loadClass(getConfigurationClassName()));
            }
            return getConfigurationClass();
        }

        /**
         * Loads the class with the specified name dynamically. If the class's
         * name is <b>null</b>, <b>null</b> will also be returned.
         *
         * @param className the name of the class to be loaded
         * @return the class object
         * @throws ClassNotFoundException if class loading fails
         * @since 1.4
         */
        protected Class loadClass(String className)
                throws ClassNotFoundException
        {
            return (className != null) ? Class.forName(className, true,
                    getClass().getClassLoader()) : null;
        }
    }

    /**
     * <p>
     * A specialized <code>BeanDeclaration</code> implementation that
     * represents the declaration of a configuration source.
     * </p>
     * <p>
     * Instances of this class are able to extract all information about a
     * configuration source from the configuration definition file. The
     * declaration of a configuration source is very similar to a bean
     * declaration processed by <code>XMLBeanDeclaration</code>. There are
     * very few differences, e.g. some reserved attributes like
     * <code>optional</code> and <code>at</code> and the fact that a bean
     * factory is never needed.
     * </p>
     */
    public static class ConfigurationDeclaration extends XMLBeanDeclaration<ConfigurationNode>
    {
        /** Stores a reference to the associated configuration builder. */
        private DefaultConfigurationBuilder configurationBuilder;

        /**
         * Creates a new instance of <code>ConfigurationDeclaration</code> and
         * initializes it.
         *
         * @param builder the associated configuration builder
         * @param config the configuration this declaration is based onto
         */
        public ConfigurationDeclaration(DefaultConfigurationBuilder builder,
                AbstractHierarchicalConfiguration<ConfigurationNode> config)
        {
            super(config);
            configurationBuilder = builder;
        }

        /**
         * Returns the associated configuration builder.
         *
         * @return the configuration builder
         */
        public DefaultConfigurationBuilder getConfigurationBuilder()
        {
            return configurationBuilder;
        }

        /**
         * Returns the value of the <code>at</code> attribute.
         *
         * @return the value of the <code>at</code> attribute (can be <b>null</b>)
         */
        public String getAt()
        {
            String result = this.getConfiguration().getString(ATTR_AT_RES);
            return (result == null) ? this.getConfiguration().getString(ATTR_AT)
                    : result;
        }

        /**
         * Returns a flag whether this is an optional configuration.
         *
         * @return a flag if this declaration points to an optional
         * configuration
         */
        public boolean isOptional()
        {
            Boolean value = this.getConfiguration().getBoolean(ATTR_OPTIONAL_RES,
                    null);
            if (value == null)
            {
                value = this.getConfiguration().getBoolean(ATTR_OPTIONAL,
                        Boolean.FALSE);
            }
            return value.booleanValue();
        }

        /**
         * Returns a flag whether this configuration should always be created
         * and added to the resulting combined configuration. This flag is
         * evaluated only for optional configurations whose normal creation has
         * caused an error. If for such a configuration the
         * <code>forceCreate</code> attribute is set and the corresponding
         * configuration provider supports this mode, an empty configuration
         * will be created and added to the resulting combined configuration.
         *
         * @return the value of the <code>forceCreate</code> attribute
         * @since 1.4
         */
        public boolean isForceCreate()
        {
            return this.getConfiguration().getBoolean(ATTR_FORCECREATE, false);
        }

        /**
         * Returns the name of the bean factory. For configuration source
         * declarations always a reserved factory is used. This factory's name
         * is returned by this implementation.
         *
         * @return the name of the bean factory
         */
        public String getBeanFactoryName()
        {
            return CONFIG_BEAN_FACTORY_NAME;
        }

        /**
         * Returns the bean's class name. This implementation will always return
         * <b>null</b>.
         *
         * @return the name of the bean's class
         */
        public String getBeanClassName()
        {
            return null;
        }

        /**
         * Checks whether the given attribute is reserved. This method will take
         * further reserved attributes into account.
         * @param parent the parent node
         * @param name the name of the affected attribute
         */
        @Override
        protected boolean isReservedAttribute(ConfigurationNode parent, String name)
        {
            if (super.isReservedAttribute(parent, name))
            {
                return true;
            }

            return ((ATTR_ATNAME.equals(name) && getNodeHandler()
                    .getAttributeValue(parent, RESERVED_PREFIX + ATTR_ATNAME) == null) || (ATTR_OPTIONALNAME
                    .equals(name) && getNodeHandler().getAttributeValue(parent,
                    RESERVED_PREFIX + ATTR_OPTIONALNAME) == null));
        }

        /**
         * Performs interpolation. This implementation will delegate
         * interpolation to the configuration builder, which takes care that the
         * currently constructed configuration is taken into account, too.
         *
         * @param value the value to be interpolated
         * @return the interpolated value
         */
        protected Object interpolate(Object value)
        {
            return getConfigurationBuilder().interpolate(value);
        }
    }

    /**
     * A specialized <code>BeanFactory</code> implementation that handles
     * configuration declarations. This class will retrieve the correct
     * configuration provider and delegate the task of creating the
     * configuration to this object.
     */
    static class ConfigurationBeanFactory implements BeanFactory
    {
        /**
         * Creates an instance of a bean class. This implementation expects that
         * the passed in bean declaration is a declaration for a configuration.
         * It will determine the responsible configuration provider and delegate
         * the call to this instance. If creation of the configuration fails
         * and the <code>optional</code> attribute is set, the exception will
         * be ignored. If the <code>forceCreate</code> attribute is set, too,
         * the provider is asked to create an empty configuration. A return
         * value of <b>null</b> means that no configuration could be created.
         *
         * @param beanClass the bean class (will be ignored)
         * @param data the declaration
         * @param param an additional parameter (will be ignored)
         * @return the newly created configuration
         * @throws Exception if an error occurs
         */
        public Object createBean(Class beanClass, BeanDeclaration data,
                Object param) throws Exception
        {
            ConfigurationDeclaration decl = (ConfigurationDeclaration) data;
            String tagName = decl.getNode().getName();
            ConfigurationProvider provider = decl.getConfigurationBuilder()
                    .providerForTag(tagName);
            if (provider == null)
            {
                throw new ConfigurationRuntimeException(
                        "No ConfigurationProvider registered for tag "
                                + tagName);
            }

            try
            {
                return provider.getConfiguration(decl);
            }
            catch (Exception ex)
            {
                // If this is an optional configuration, ignore the exception
                if (!decl.isOptional())
                {
                    throw ex;
                }
                else
                {
                    // Notify registered error listeners
                    decl.getConfigurationBuilder().fireError(
                            EVENT_ERR_LOAD_OPTIONAL,
                            decl.getConfiguration().getString(ATTR_NAME), null,
                            ex);

                    if (decl.isForceCreate())
                    {
                        try
                        {
                            return provider.getEmptyConfiguration(decl);
                        }
                        catch (Exception ex2)
                        {
                            // Ignore exception, return null in this case
                            ;
                        }
                    }
                    return null;
                }
            }
        }

        /**
         * Returns the default class for this bean factory.
         *
         * @return the default class
         */
        public Class getDefaultBeanClass()
        {
            // Here some valid class must be returned, otherwise BeanHelper
            // will complain that the bean's class cannot be determined
            return Configuration.class;
        }
    }

    /**
     * A specialized provider implementation that deals with file based
     * configurations. Ensures that the base path is correctly set and that the
     * load() method gets called.
     */
    public static class FileConfigurationProvider extends ConfigurationProvider
    {
        /**
         * Creates a new instance of <code>FileConfigurationProvider</code>.
         */
        public FileConfigurationProvider()
        {
            super();
        }

        /**
         * Creates a new instance of <code>FileConfigurationProvider</code>
         * and sets the configuration class.
         *
         * @param configClass the class for the configurations to be created
         */
        public FileConfigurationProvider(Class configClass)
        {
            super(configClass);
        }

        /**
         * Creates a new instance of <code>FileConfigurationProvider</code>
         * and sets the configuration class name.
         *
         * @param configClassName the name of the configuration to be created
         * @since 1.4
         */
        public FileConfigurationProvider(String configClassName)
        {
            super(configClassName);
        }

        /**
         * Creates the configuration. After that <code>load()</code> will be
         * called. If this configuration is marked as optional, exceptions will
         * be ignored.
         *
         * @param decl the declaration
         * @return the new configuration
         * @throws Exception if an error occurs
         */
        public AbstractConfiguration getConfiguration(
                ConfigurationDeclaration decl) throws Exception
        {
            AbstractConfiguration result = getEmptyConfiguration(decl);
            ((FileConfiguration) result).load();
            return result;
        }

        /**
         * Returns an uninitialized file configuration. This method will be
         * called for optional configurations when the
         * <code>getConfiguration()</code> method caused an error and the
         * <code>forceCreate</code> attribute is set. It will create the
         * configuration of the represented type, but the <code>load()</code>
         * method won't be called. This way non-existing configuration files can
         * be handled gracefully: If loading a the file fails, an empty
         * configuration will be created that is already configured with the
         * correct file name.
         *
         * @param decl the bean declaration with initialization parameters for
         * the configuration
         * @return the new configuration object
         * @throws Exception if an error occurs
         * @since 1.4
         */
        public AbstractConfiguration getEmptyConfiguration(
                ConfigurationDeclaration decl) throws Exception
        {
            return super.getConfiguration(decl);
        }

        /**
         * Initializes the bean instance. Ensures that the file configuration's
         * base path will be initialized with the base path of the factory so
         * that relative path names can be correctly resolved.
         *
         * @param bean the bean to be initialized
         * @param data the declaration
         * @throws Exception if an error occurs
         */
        protected void initBeanInstance(Object bean, BeanDeclaration data)
                throws Exception
        {
            FileConfiguration config = (FileConfiguration) bean;
            config.setBasePath(((ConfigurationDeclaration) data)
                    .getConfigurationBuilder().getConfigurationBasePath());
            super.initBeanInstance(bean, data);
        }
    }

    /**
     * A specialized configuration provider for XML configurations. This
     * implementation acts like a <code>FileConfigurationProvider</code>, but
     * it will copy all entity IDs that have been registered for the
     * configuration builder to the new XML configuration before it is loaded.
     */
    static class XMLConfigurationProvider extends FileConfigurationProvider
    {
        /**
         * Creates a new instance of <code>XMLConfigurationProvider</code>.
         */
        public XMLConfigurationProvider()
        {
            super(XMLConfiguration.class);
        }

        /**
         * Returns a new empty configuration instance. This implementation
         * performs some additional initialization specific to XML
         * configurations.
         *
         * @param decl the configuration declaration
         * @return the new configuration
         * @throws Exception if an error occurs
         */
        public AbstractConfiguration getEmptyConfiguration(ConfigurationDeclaration decl) throws Exception
        {
            XMLConfiguration config = (XMLConfiguration) super.getEmptyConfiguration(decl);

            // copy the registered entities
            DefaultConfigurationBuilder builder = decl.getConfigurationBuilder();
            config.getRegisteredEntities().putAll(builder.getRegisteredEntities());
            return config;
        }
    }

    /**
     * A specialized configuration provider for file based configurations that
     * can handle configuration sources whose concrete type depends on the
     * extension of the file to be loaded. One example is the
     * <code>properties</code> tag: if the file ends with ".xml" a
     * XMLPropertiesConfiguration object must be created, otherwise a
     * PropertiesConfiguration object.
     */
    static class FileExtensionConfigurationProvider extends FileConfigurationProvider
    {
        /**
         * Stores the class to be created when the file extension matches.
         */
        private Class matchingClass;

        /**
         * Stores the name of the class to be created when the file extension
         * matches.
         */
        private String matchingClassName;

        /**
         * Stores the class to be created when the file extension does not
         * match.
         */
        private Class defaultClass;

        /**
         * Stores the name of the class to be created when the file extension
         * does not match.
         */
        private String defaultClassName;

        /** Stores the file extension to be checked against. */
        private String fileExtension;

        /**
         * Creates a new instance of
         * <code>FileExtensionConfigurationProvider</code> and initializes it.
         *
         * @param matchingClass the class to be created when the file extension
         * matches
         * @param defaultClass the class to be created when the file extension
         * does not match
         * @param extension the file extension to be checked agains
         */
        public FileExtensionConfigurationProvider(Class matchingClass,
                Class defaultClass, String extension)
        {
            this.matchingClass = matchingClass;
            this.defaultClass = defaultClass;
            fileExtension = extension;
        }

        /**
         * Creates a new instance of
         * <code>FileExtensionConfigurationProvider</code> and initializes it
         * with the names of the classes to be created.
         *
         * @param matchingClassName the name of the class to be created when the
         * file extension matches
         * @param defaultClassName the name of the class to be created when the
         * file extension does not match
         * @param extension the file extension to be checked against
         * @since 1.4
         */
        public FileExtensionConfigurationProvider(String matchingClassName,
                String defaultClassName, String extension)
        {
            this.matchingClassName = matchingClassName;
            this.defaultClassName = defaultClassName;
            fileExtension = extension;
        }

        /**
         * Returns the matching class object, no matter whether it was defined
         * as a class or as a class name.
         *
         * @return the matching class object
         * @throws Exception if an error occurs
         * @since 1.4
         */
        protected synchronized Class fetchMatchingClass() throws Exception
        {
            if (matchingClass == null)
            {
                matchingClass = loadClass(matchingClassName);
            }
            return matchingClass;
        }

        /**
         * Returns the default class object, no matter whether it was defined as
         * a class or as a class name.
         *
         * @return the default class object
         * @throws Exception if an error occurs
         * @since 1.4
         */
        protected synchronized Class fetchDefaultClass() throws Exception
        {
            if (defaultClass == null)
            {
                defaultClass = loadClass(defaultClassName);
            }
            return defaultClass;
        }

        /**
         * Creates the configuration object. The class is determined by the file
         * name's extension.
         *
         * @param beanClass the class
         * @param data the bean declaration
         * @return the new bean
         * @throws Exception if an error occurs
         */
        protected Object createBeanInstance(Class beanClass,
                BeanDeclaration data) throws Exception
        {
            String fileName = ((ConfigurationDeclaration) data)
                    .getConfiguration().getString(ATTR_FILENAME);
            if (fileName != null
                    && fileName.toLowerCase().trim().endsWith(fileExtension))
            {
                return super.createBeanInstance(fetchMatchingClass(), data);
            }
            else
            {
                return super.createBeanInstance(fetchDefaultClass(), data);
            }
        }
    }

    /**
     * A specialized configuration provider class that allows to include other
     * configuration definition files.
     */
    static class ConfigurationBuilderProvider extends ConfigurationProvider
    {
        /**
         * Creates a new instance of <code>ConfigurationBuilderProvider</code>.
         */
        public ConfigurationBuilderProvider()
        {
            super(DefaultConfigurationBuilder.class);
        }

        /**
         * Creates the configuration. First creates a configuration builder
         * object. Then returns the configuration created by this builder.
         *
         * @param decl the configuration declaration
         * @return the configuration
         * @exception Exception if an error occurs
         */
        public AbstractConfiguration getConfiguration(
                ConfigurationDeclaration decl) throws Exception
        {
            DefaultConfigurationBuilder builder = (DefaultConfigurationBuilder) super
                    .getConfiguration(decl);
            return builder.getConfiguration(true);
        }

        /**
         * Returns an empty configuration in case of an optional configuration
         * could not be created. This implementation returns an empty combined
         * configuration.
         *
         * @param decl the configuration declaration
         * @return the configuration
         * @exception Exception if an error occurs
         * @since 1.4
         */
        public AbstractConfiguration getEmptyConfiguration(
                ConfigurationDeclaration decl) throws Exception
        {
            return new CombinedConfiguration();
        }
    }

    /**
     * A specialized configuration provider for database configuration.
     * The datasource is specified as a JNDI key for the default context.
     */
    static class DatabaseConfigurationProvider extends ConfigurationProvider
    {
        public AbstractConfiguration getConfiguration(ConfigurationDeclaration decl) throws Exception
        {
            Map attributes = decl.getBeanProperties();
            String jndi = (String) attributes.get("jndi");
            DataSource datasource = (DataSource) new InitialContext().lookup(jndi);

            String table = (String) attributes.get("table");
            String nameColumn = (String) attributes.get("nameColumn");
            String name = (String) attributes.get("name");
            String keyColumn = (String) attributes.get("keyColumn");
            String valueColumn = (String) attributes.get("valueColumn");

            DatabaseConfiguration config;
            if (name == null) {
                config = new DatabaseConfiguration(datasource, table, keyColumn, valueColumn);
            } else {
                config = new DatabaseConfiguration(datasource, table, nameColumn, keyColumn, valueColumn, name);
            }

            // initialize the other properties
            BeanHelper.initBean(config, decl, true);

            return config;
        }
    }
}
