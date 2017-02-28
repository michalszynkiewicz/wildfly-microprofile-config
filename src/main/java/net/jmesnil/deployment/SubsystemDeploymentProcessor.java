package net.jmesnil.deployment;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import net.jmesnil.extension.microprofile.config.impl.PropertiesConfigSource;
import net.jmesnil.extension.microprofile.config.impl.WildFlyConfigBuilder;
import net.jmesnil.extension.microprofile.config.impl.WildFlyConfigProviderResolver;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.server.deployment.SubDeploymentMarker;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleRootMarker;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.vfs.VirtualFile;

/**
 */
public class SubsystemDeploymentProcessor implements DeploymentUnitProcessor {

    Logger log = Logger.getLogger(SubsystemDeploymentProcessor.class);

    /**
     * See {@link Phase} for a description of the different phases
     */
    public static final Phase PHASE = Phase.DEPENDENCIES;

    /**
     * The relative order of this processor within the {@link #PHASE}.
     * The current number is large enough for it to happen after all
     * the standard deployment unit processors that come with JBoss AS.
     */
    public static final int PRIORITY = 0x4000;

    public static final ModuleIdentifier MICROPROFILE_CONFIG_API = ModuleIdentifier.create("org.eclipse.microprofile.config.api");
    private static final String META_INF_MICROPROFILE_CONFIG_PROPERTIES = "META-INF/microprofile-config.properties";
    private static final String WEB_INF_MICROPROFILE_CONFIG_PROPERTIES = "WEB-INF/classes/META-INF/microprofile-config.properties";

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {

        ConfigProviderResolver.setInstance(WildFlyConfigProviderResolver.INSTANCE);

        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        addDependencies(deploymentUnit);

        WildFlyConfigBuilder builder = new WildFlyConfigBuilder();
        builder.addDefaultSources();

        List<ResourceRoot> structure = deploymentUnit.getAttachmentList(Attachments.RESOURCE_ROOTS);
        for (ResourceRoot resourceRoot : structure) {
            if (ModuleRootMarker.isModuleRoot(resourceRoot) && !SubDeploymentMarker.isSubDeployment(resourceRoot)) {
                load(builder, resourceRoot, META_INF_MICROPROFILE_CONFIG_PROPERTIES);
                load(builder, resourceRoot, WEB_INF_MICROPROFILE_CONFIG_PROPERTIES);
            }
        }
        Config config = builder.build();
        WildFlyConfigProviderResolver.INSTANCE.setDefaultConfig(config);
    }

    private void load(ConfigProvider.ConfigBuilder builder, ResourceRoot resourceRoot, String path) {
        VirtualFile configProperties = resourceRoot.getRoot().getChild(path);
        if (configProperties.exists() && configProperties.isFile()) {
            log.infof("founds properties: %s", configProperties.toString());
            Properties properties = new Properties();
            try (InputStream in = configProperties.openStream()) {
                properties.load(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
            properties.list(System.out);
            builder.withSources(new PropertiesConfigSource(properties, configProperties.getPathName()));
        }
    }

    @Override
    public void undeploy(DeploymentUnit context) {
    }

    private void addDependencies(DeploymentUnit deploymentUnit) {
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);
        final ModuleLoader moduleLoader = Module.getBootModuleLoader();

        moduleSpecification.addSystemDependency(new ModuleDependency(moduleLoader, MICROPROFILE_CONFIG_API, false, false, true, false));
    }

}