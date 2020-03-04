/*
 * Copyright (c) 2002-2020, City of Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.directories.modules.solr.indexer;

import fr.paris.lutece.plugins.directories.business.DirectoryEntity;
import fr.paris.lutece.plugins.directories.service.DirectoriesService;
import fr.paris.lutece.plugins.directories.util.DirectoriesUtils;
import fr.paris.lutece.plugins.genericattributes.business.Response;
import fr.paris.lutece.plugins.search.solr.business.field.Field;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexer;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexerService;
import fr.paris.lutece.plugins.search.solr.indexer.SolrItem;
import fr.paris.lutece.plugins.search.solr.util.SolrConstants;
import fr.paris.lutece.portal.service.util.AppException;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;
import fr.paris.lutece.util.url.UrlItem;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

/**
 * The indexer service for Solr.
 *
 */
public class SolrDocIndexer implements SolrIndexer
{
    public static final String BEAN_NAME = "directories-solr.solrDocIndexer";
    private static final String TYPE = "directories";
    private static final String PARAMETER_ENTITY_ID = "entity_id";
    private static final String PROPERTY_INDEXER_ENABLE = "solr.indexer.document.enable";
    private static final String PROPERTY_DOCUMENT_MAX_CHARS = "directories-solr.indexer.document.characters.limit";
    private static final String PROPERTY_NAME = "directories-solr.indexer.name";
    private static final String PROPERTY_DESCRIPTION = "directories-solr.indexer.description";
    private static final String PROPERTY_VERSION = "directories-solr.indexer.version";
    private static final String PARAMETER_XPAGE = "page";
    private static final String XPAGE_DIRECTORIES = "directories";
    private static final String PARAMETER_VIEW = "view";
    private static final String PARAMETER_VIEW_ENTITY = "viewDirectoryEntity";
    private static final List<String> LIST_RESSOURCES_NAME = new ArrayList<String>( );
    private static final String SHORT_NAME = "entity";
    private static final String DOC_INDEXATION_ERROR = "[SolrDirectoriesIndexer] An error occured during the indexation of the document number ";
    private static final Integer PARAMETER_DOCUMENT_MAX_CHARS = Integer.parseInt( AppPropertiesService.getProperty( PROPERTY_DOCUMENT_MAX_CHARS ) );

    /**
     * Creates a new SolrPageIndexer
     */
    public SolrDocIndexer( )
    {
        LIST_RESSOURCES_NAME.add( DirectoriesUtils.CONSTANT_TYPE_RESOURCE );
    }

    @Override
    public boolean isEnable( )
    {
        return "true".equalsIgnoreCase( AppPropertiesService.getProperty( PROPERTY_INDEXER_ENABLE ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> indexDocuments( )
    {
        List<String> lstErrors = new ArrayList<String>( );
        List<Integer> listDocument = new ArrayList<Integer>( );
        Collection<SolrItem> solrItems = new ArrayList<SolrItem>( );
        for ( DirectoryEntity document : DirectoriesService.getInstance( ).getListDocWithoutBinaries( ) )
        {
            try
            {
                if ( !listDocument.contains( document.getId( ) ) )
                {
                    // Generates the item to index
                    SolrItem item = getItem( document );
                    if ( item != null )
                    {
                        solrItems.add( item );
                    }
                    listDocument.add( document.getId( ) );
                }
            }
            catch( Exception e )
            {
                lstErrors.add( SolrIndexerService.buildErrorMessage( e ) );
                AppLogService.error( DOC_INDEXATION_ERROR + document.getId( ), e );
            }
        }
        try
        {
            SolrIndexerService.write( solrItems );
        }
        catch( Exception e )
        {
            lstErrors.add( SolrIndexerService.buildErrorMessage( e ) );
            AppLogService.error( DOC_INDEXATION_ERROR, e );
        }
        return lstErrors;
    }

    /**
     * Get item
     * 
     * @param portlet
     *            The portlet
     * @param document
     *            The document
     * @return The item
     * @throws IOException
     */
    private SolrItem getItem( DirectoryEntity document ) throws IOException
    {
        // the item
        SolrItem item = new SolrItem( );
        item.setUid( getResourceUid( Integer.valueOf( document.getId( ) ).toString( ), DirectoriesUtils.CONSTANT_TYPE_RESOURCE ) );
        item.setDate( document.getCreation( ) );
        item.setType( TYPE );
        item.setSite( SolrIndexerService.getWebAppName( ) );
        item.setRole( "none" );
        item.setTitle( document.getTitle( ) );
        // Reload the full object to get all its searchable attributes
        UrlItem url = new UrlItem( SolrIndexerService.getBaseUrl( ) );
        url.addParameter( PARAMETER_XPAGE, XPAGE_DIRECTORIES );
        url.addParameter( PARAMETER_VIEW, PARAMETER_VIEW_ENTITY );
        url.addParameter( PARAMETER_ENTITY_ID, document.getId( ) );
        item.setUrl( url.getUrl( ) );
        // Date Hierarchy
        GregorianCalendar calendar = new GregorianCalendar( );
        calendar.setTime( document.getCreation( ) );
        item.setHieDate( calendar.get( GregorianCalendar.YEAR ) + "/" + ( calendar.get( GregorianCalendar.MONTH ) + 1 ) + "/"
                + calendar.get( GregorianCalendar.DAY_OF_MONTH ) );
        // The content
        String strContentToIndex = getContentToIndex( document, item );
        ContentHandler handler = null;
        if ( PARAMETER_DOCUMENT_MAX_CHARS != null )
        {
            handler = new BodyContentHandler( PARAMETER_DOCUMENT_MAX_CHARS );
        }
        else
        {
            handler = new BodyContentHandler( );
        }
        Metadata metadata = new Metadata( );
        try
        {
            new HtmlParser( ).parse( new ByteArrayInputStream( strContentToIndex.getBytes( ) ), handler, metadata, new ParseContext( ) );
        }
        catch( SAXException e )
        {
            throw new AppException( "Error during document parsing." );
        }
        catch( TikaException e )
        {
            throw new AppException( "Error during document parsing." );
        }
        item.setContent( handler.toString( ) );
        return item;
    }

    /**
     * GEt the content to index
     * 
     * @param document
     *            The document
     * @param item
     *            The SolR item
     * @return The content
     */
    private static String getContentToIndex( DirectoryEntity document, SolrItem item )
    {
        StringBuilder sbContentToIndex = new StringBuilder( );
        for ( Response response : document.getResponses( ) )
        {
            String value = response.getResponseValue( );
            if ( value != null && !value.equals( "null" ) )
            {
                sbContentToIndex.append( " " );
                sbContentToIndex.append( value );
                String strFieldName = "attribute" + response.getEntry( ).getIdEntry( );
                item.addDynamicField( strFieldName, fillDynamicField( item, strFieldName, value ) );
            }
        }
        return sbContentToIndex.toString( );
    }

    // GETTERS & SETTERS
    /**
     * Returns the name of the indexer.
     *
     * @return the name of the indexer
     */
    @Override
    public String getName( )
    {
        return AppPropertiesService.getProperty( PROPERTY_NAME );
    }

    /**
     * Returns the version.
     *
     * @return the version.
     */
    @Override
    public String getVersion( )
    {
        return AppPropertiesService.getProperty( PROPERTY_VERSION );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription( )
    {
        return AppPropertiesService.getProperty( PROPERTY_DESCRIPTION );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Field> getAdditionalFields( )
    {
        List<Field> lstFields = new ArrayList<Field>( );
        return lstFields;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SolrItem> getDocuments( String strIdDocument )
    {
        List<SolrItem> lstItems = new ArrayList<SolrItem>( );
        return lstItems;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getResourcesName( )
    {
        return LIST_RESSOURCES_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getResourceUid( String strResourceId, String strResourceType )
    {
        StringBuilder sb = new StringBuilder( strResourceId );
        sb.append( SolrConstants.CONSTANT_UNDERSCORE ).append( SHORT_NAME );
        return sb.toString( );
    }

    /**
     * {@inheritDoc}
     */
    private static String fillDynamicField( SolrItem solrItem, String strFieldName, String strDynamicFieldName )
    {
        Map<String, Object> listDynamicFields = solrItem.getDynamicFields( );
        for ( Object key : listDynamicFields.keySet( ) )
        {
            if ( key.toString( ).equals( strFieldName + "_text" ) )
            {
                strDynamicFieldName = strDynamicFieldName + " " + listDynamicFields.get( key );
            }
        }
        return strDynamicFieldName;
    }
}
