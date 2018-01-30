/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * @author Arne Kepp, OpenGeo, Copyright 2009
 */
package org.geowebcache.grid;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.config.DefaultGridsets;
import org.geowebcache.config.GridSetConfiguration;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.layer.TileLayerDispatcher;

public class GridSetBroker {
    private static Log log = LogFactory.getLog(GridSetBroker.class);
    
    private List<GridSetConfiguration> configurations;

    private DefaultGridsets defaults;

    public GridSetBroker(boolean useEPSG900913, boolean useGWC11xNames) {
        configurations = new LinkedList<>();
        defaults = new DefaultGridsets(useEPSG900913, useGWC11xNames);
        configurations.add(defaults);
    }
    
    public GridSetBroker(List<GridSetConfiguration> configurations) {
        this.configurations = configurations;
        defaults = configurations.stream()
            .filter(DefaultGridsets.class::isInstance)
            .findFirst()
            .map(DefaultGridsets.class::cast)
            .get();
    }

    public void initialize() {
        configurations = GeoWebCacheExtensions.configurations(GridSetConfiguration.class);
    }
    
    public @Nullable GridSet get(String gridSetId) {
        return getGridSet(gridSetId).orElse(null);
    }

    protected Optional<GridSet> getGridSet(String name) {
        return configurations.stream()
            .map(c->c.getGridSet(name))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    /**
     * @return the names of the gridsets that are internally defined
     */
    public Set<String> getEmbeddedNames() {
        return defaults.getGridSetNames();
    }

    public Set<String> getNames() {
        return getGridSetNames();
    }
    
    public Set<String> getGridSetNames() {
        return configurations.stream()
                .map(GridSetConfiguration::getGridSetNames)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    public Collection<GridSet> getGridSets() {
        return configurations.stream()
                .map(GridSetConfiguration::getGridSets)
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(
                        GridSet::getName, 
                        g->g, 
                        (g1, g2)->g1, // Prefer the first one 
                        HashMap::new))
                .values();
    }

    public synchronized void put(GridSet gridSet) {
        remove(gridSet.getName());
        addGridSet(gridSet);
    }
    
    public void addGridSet(GridSet gridSet) {
        log.debug("Adding " + gridSet.getName());
        configurations.stream()
            .filter(c->c.canSave(gridSet))
            .findFirst()
            .orElseThrow(()-> new UnsupportedOperationException("No Configuration is able to save gridset "+gridSet.getName()))
            .addGridSet(gridSet);
    }

    /**
     * Blindly removes a gridset from this gridset broker.
     * <p>
     * This method doesn't check whether there's any layer referencing the gridset nor removes it
     * from the {@link XMLConfiguration}. For such a thing, check
     * {@link TileLayerDispatcher#removeGridset(String)}, which cascades to this method.
     * </p>
     * 
     * @param gridSetName
     * @return
     */
    public synchronized GridSet remove(final String gridSetName) {
        return getGridSet(gridSetName).map(g -> {
                removeGridSet(gridSetName);
                return g;
            }).orElse(null);
    }

    public synchronized void removeGridSet(final String gridSetName) {
        configurations.stream()
            .filter(c->c.getGridSet(gridSetName).isPresent())
            .forEach(c->{c.removeGridSet(gridSetName);});
    }
    
    public GridSet getWorldEpsg4326() {
        return defaults.worldEpsg4326();
    }

    public GridSet getWorldEpsg3857() {
        return defaults.worldEpsg3857();
    }

}