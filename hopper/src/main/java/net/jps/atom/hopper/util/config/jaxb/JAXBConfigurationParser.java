package net.jps.atom.hopper.util.config.jaxb;

import net.jps.atom.hopper.util.config.AbstractConfigurationParser;
import net.jps.atom.hopper.util.config.ConfigurationParserException;
import net.jps.atom.hopper.util.log.Logger;
import net.jps.atom.hopper.util.log.RCLogger;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.bind.*;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.net.URL;

public final class JAXBConfigurationParser<T> extends AbstractConfigurationParser<T> {

    private static final SchemaFactory SCHEMA_FACTORY = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    private static final Logger LOG = new RCLogger(JAXBConfigurationParser.class);
    private final JAXBContext jaxbContext;
    private Schema validationSchema;

    public JAXBConfigurationParser(Class<T> configClass, Class<?>... objectFactories) {
        super(configClass);

        validationSchema = null;

        try {
            jaxbContext = JAXBContext.newInstance(objectFactories);
        } catch (JAXBException jaxbex) {
            throw LOG.newException("Failed to create the JAXB context required for configuration marshalling", jaxbex, ConfigurationParserException.class);
        }
    }

    /**
     * Reads in an arbitrary length array of urls and creates a combined schema
     * from them.
     *
     * Note: Order matters! Base schemas should always be loaded first.
     *
     * @param schemaUrls
     * @throws IOException
     * @throws SAXException
     */
    public void enableValidation(URL... schemaUrls) throws IOException, SAXException {
        //This may be inefficient for a large number of schemas since I imagine
        //each stream source holds onto a FD until its internal stream is done
        //being read
        final StreamSource[] streamSourceArray = new StreamSource[schemaUrls.length];

        for (int i = 0; i < schemaUrls.length; i++) {
            streamSourceArray[i] = new StreamSource(schemaUrls[i].openStream());
        }

        validationSchema = SCHEMA_FACTORY.newSchema(streamSourceArray);
    }

    private Unmarshaller createUnmarshaller() throws JAXBException {
        final Unmarshaller freshUnmarhsaller = jaxbContext.createUnmarshaller();

        if (validationSchema != null) {
            freshUnmarhsaller.setSchema(validationSchema);
        }

        return freshUnmarhsaller;
    }

    @Override
    protected T readConfiguration() {
        T rootConfigElement;

        try {
            final Object unmarshaledObj = createUnmarshaller().unmarshal(getConfigurationResource().getInputStream());

            if (getConfigurationClass().isInstance(unmarshaledObj)) {
                rootConfigElement = (T) unmarshaledObj;
            } else if (unmarshaledObj instanceof JAXBElement) {
                rootConfigElement = ((JAXBElement<T>) unmarshaledObj).getValue();
            } else {
                throw LOG.newException("Failed to read config and no exception was thrown. Potential bug. Please report this.", ConfigurationParserException.class);
            }
        } catch (UnmarshalException mue) {
            throw LOG.newException("Your configuration may be malformed. Please review it and make sure it validates correctly. Reason: "
                    + mue.getMessage(), mue, ConfigurationParserException.class);
        } catch (Exception ex) {
            throw LOG.newException("Failed to read the configuration. Reason: "
                    + "" + ex.getMessage()
                    + " - pump cause for more details", ex, ConfigurationParserException.class);
        }

        return rootConfigElement;
    }
}
