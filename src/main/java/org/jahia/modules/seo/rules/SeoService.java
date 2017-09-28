/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2017 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.seo.rules;

import org.drools.core.spi.KnowledgeHelper;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.rules.AddedNodeFact;
import org.jahia.services.seo.VanityUrl;
import org.jahia.services.seo.jcr.VanityUrlService;
import org.slf4j.Logger;

import javax.jcr.RepositoryException;
import javax.validation.ConstraintViolationException;
import java.util.List;

/**
 * SEO service class for manipulating content URL mappings from the
 * right-hand-side (consequences) of rules.
 *
 * @author Sergiy Shyrkov
 */
public class SeoService {

    private static Logger logger = org.slf4j.LoggerFactory.getLogger(SeoService.class);

    private VanityUrlService urlService;

    /**
     * Adds the URL mapping for the specified node and language.
     *
     * @param node      the node add mappings to
     * @param locale    the language code to add mappings for
     * @param url       the URL for the mapping
     * @param isDefault set the new mapping as default one
     * @param drools    the rule engine helper class
     * @throws RepositoryException in case of an error
     */
    public void addMapping(final AddedNodeFact node, final String locale, final String url, final boolean isDefault,
                           KnowledgeHelper drools) throws RepositoryException {
        if (logger.isDebugEnabled()) {
            logger.debug("Adding URL mapping for node " + node.getPath() + " and locale '" + locale + "'");
        }
        final String path = node.getPath();
        String urlToTry = url;
        int index = 0;
        String siteKey = JCRContentUtils.getSiteKey(path);
        JCRNodeWrapper nodeWrapper = node.getNode();
        while (true) {
            try {
                urlService.saveVanityUrlMapping(nodeWrapper, new VanityUrl(urlToTry, siteKey, locale, isDefault, true));
                break;
            } catch (ConstraintViolationException ex) {
                urlToTry = url + "-" + (++index);
            }
        }
    }

    /**
     * Removes all URL mappings for the specified node and language.
     *
     * @param node   the node to remove mappings from
     * @param locale the language code to remove mappings for
     * @param drools the rule engine helper class
     * @throws RepositoryException in case of an error
     */
    public void removeMappings(final AddedNodeFact node, final String locale, KnowledgeHelper drools)
            throws RepositoryException {
        if (logger.isDebugEnabled()) {
            logger.debug("Removing URL mappings for locale '" + locale + "' from node " + node.getPath());
        }
        urlService.removeVanityUrlMappings(node.getNode(), locale);
    }

    public void checkVanityUrl(final AddedNodeFact newSeo, KnowledgeHelper drools) {
        JCRNodeWrapper node = newSeo.getNode();
        try {
            String url = node.getProperty("j:url").getString();
            List<VanityUrl> result;

            int i = 1;

            String baseurl = url;
            int dot = baseurl.lastIndexOf('.');
            String ext = "";
            if (dot > 0) {
                ext = baseurl.substring(dot);
                baseurl = baseurl.substring(0, dot);
            }
            int und = baseurl.lastIndexOf('-');
            if (und > -1 && baseurl.substring(und + 1).matches("[0-9]+")) {
                baseurl = baseurl.substring(0, und);
            }

            boolean changed = false;
            do {
                result = urlService.findExistingVanityUrls(url, node.getResolveSite().getSiteKey(), node.getSession().getWorkspace().getName());
                if (result.size() > (changed ? 0 : 1)) {
                    url = baseurl + "-" + (i++) + ext;
                    changed = true;
                } else {
                    break;
                }
            } while (true);
            if (changed) {
                node.setProperty("j:url", url);
                urlService.flushCacheEntry(urlService.getCacheByUrlKey(url,node.getResolveSite().getSiteKey(), node.getSession().getWorkspace().getName()));
            }
        } catch (RepositoryException e) {
            logger.error(e.getMessage(),e);
        }
    }

    /**
     * Injects an instance of the {@link VanityUrlService}.
     *
     * @param urlService an instance of the {@link VanityUrlService}
     */
    public void setUrlService(VanityUrlService urlService) {
        this.urlService = urlService;
    }

}