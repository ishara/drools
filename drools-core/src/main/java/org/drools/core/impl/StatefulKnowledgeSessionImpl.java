/*
 * Copyright 2010 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.core.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.drools.core.FactException;
import org.drools.core.RuleBase;
import org.drools.core.WorkingMemory;
import org.drools.core.command.impl.FixedKnowledgeCommandContext;
import org.drools.core.command.impl.GenericCommand;
import org.drools.core.command.impl.KnowledgeCommandContext;
import org.drools.core.command.runtime.BatchExecutionCommandImpl;
import org.drools.core.common.AbstractWorkingMemory;
import org.drools.core.common.EndOperationListener;
import org.drools.core.common.InternalAgenda;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalKnowledgeRuntime;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.InternalWorkingMemoryEntryPoint;
import org.drools.core.common.ObjectStore;
import org.drools.core.common.ObjectTypeConfigurationRegistry;
import org.drools.core.common.WorkingMemoryAction;
import org.drools.core.event.ActivationCancelledEvent;
import org.drools.core.event.ActivationCreatedEvent;
import org.drools.core.event.AfterActivationFiredEvent;
import org.drools.core.event.AgendaGroupPoppedEvent;
import org.drools.core.event.AgendaGroupPushedEvent;
import org.drools.core.event.BeforeActivationFiredEvent;
import org.drools.core.event.ObjectInsertedEvent;
import org.drools.core.event.ObjectRetractedEvent;
import org.drools.core.event.ObjectUpdatedEvent;
import org.drools.core.event.RuleFlowGroupActivatedEvent;
import org.drools.core.event.RuleFlowGroupDeactivatedEvent;
import org.drools.core.event.rule.impl.ActivationCancelledEventImpl;
import org.drools.core.event.rule.impl.ActivationCreatedEventImpl;
import org.drools.core.event.rule.impl.AfterActivationFiredEventImpl;
import org.drools.core.event.rule.impl.AgendaGroupPoppedEventImpl;
import org.drools.core.event.rule.impl.AgendaGroupPushedEventImpl;
import org.drools.core.event.rule.impl.BeforeActivationFiredEventImpl;
import org.drools.core.event.rule.impl.ObjectDeletedEventImpl;
import org.drools.core.event.rule.impl.ObjectInsertedEventImpl;
import org.drools.core.event.rule.impl.ObjectUpdatedEventImpl;
import org.drools.core.event.rule.impl.RuleFlowGroupActivatedEventImpl;
import org.drools.core.event.rule.impl.RuleFlowGroupDeactivatedEventImpl;
import org.drools.core.reteoo.DisposedReteooWorkingMemory;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.ReteooWorkingMemoryInterface;
import org.drools.core.rule.EntryPointId;
import org.drools.core.rule.Rule;
import org.drools.core.runtime.impl.ExecutionResultImpl;
import org.drools.core.runtime.process.InternalProcessRuntime;
import org.drools.core.runtime.rule.impl.AgendaImpl;
import org.drools.core.runtime.rule.impl.NativeQueryResults;
import org.drools.core.spi.Activation;
import org.drools.core.time.TimerService;
import org.kie.api.runtime.rule.TimedRuleExecutionFilter;
import org.kie.internal.KnowledgeBase;
import org.kie.api.command.Command;
import org.kie.internal.command.Context;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.WorkingMemoryEventListener;
import org.kie.internal.process.CorrelationAwareProcessRuntime;
import org.kie.internal.process.CorrelationKey;
import org.kie.internal.runtime.StatefulKnowledgeSession;
import org.kie.api.runtime.Calendars;
import org.kie.api.runtime.Channel;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.ExecutionResults;
import org.kie.api.runtime.Globals;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.api.runtime.rule.Agenda;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.LiveQuery;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.ViewChangedEventListener;
import org.kie.api.time.SessionClock;

public class StatefulKnowledgeSessionImpl extends AbstractRuntime
        implements
        StatefulKnowledgeSession,
        InternalWorkingMemoryEntryPoint,
        InternalKnowledgeRuntime,
        KieSession,
        CorrelationAwareProcessRuntime {

    public ReteooWorkingMemoryInterface session;
    public KnowledgeBase                kbase;

    public StatefulKnowledgeSessionImpl(AbstractWorkingMemory session) {
        this( session,
              new KnowledgeBaseImpl( session.getRuleBase() ) );
    }

    public StatefulKnowledgeSessionImpl(AbstractWorkingMemory session,
                                        KnowledgeBase kbase) {
        this.session = session;
        this.kbase = kbase;
        this.session.setKnowledgeRuntime( this );
    }

    public void reset() {
        throw new UnsupportedOperationException( "This should not be called" );
    }

    public ObjectStore getObjectStore() {
        return this.session.getObjectStore();
    }

    public EntryPointNode getEntryPointNode() {
        return this.session.getEntryPointNode();
    }

    public int getId() {
        return this.session.getId();
    }

    public EntryPoint getEntryPoint(String name) {
        return session.getWorkingMemoryEntryPoint( name );
    }

    public Collection< ? extends org.kie.api.runtime.rule.EntryPoint> getEntryPoints() {
        return session.getWorkingMemoryEntryPoints();
    }

    public void addEventListener(WorkingMemoryEventListener listener) {
        WorkingMemoryEventListenerWrapper wrapper = new WorkingMemoryEventListenerWrapper( listener );
        this.session.addEventListener( wrapper );
    }

    public void removeEventListener(WorkingMemoryEventListener listener) {
        WorkingMemoryEventListenerWrapper wrapper;
        if ( listener != null && !(listener instanceof WorkingMemoryEventListenerWrapper) ) {
            wrapper = new WorkingMemoryEventListenerWrapper( listener );
        } else {
            wrapper = (WorkingMemoryEventListenerWrapper) listener;
        }
        this.session.removeEventListener( wrapper );
    }

    public Collection<WorkingMemoryEventListener> getWorkingMemoryEventListeners() {
        // TODO incompatible with the javadoc of the implemented method which states "Returns all event listeners"
        List<WorkingMemoryEventListener> listeners = new ArrayList<WorkingMemoryEventListener>();
        for ( Object listener : this.session.getWorkingMemoryEventListeners() ) {
            if ( listener instanceof WorkingMemoryEventListenerWrapper ) {
                listeners.add( ((WorkingMemoryEventListenerWrapper) listener).unWrap() );
            } else if (listener instanceof WorkingMemoryEventListener) {
                listeners.add( (WorkingMemoryEventListener) listener );
            }
        }
        return Collections.unmodifiableCollection( listeners );
    }

    public void addEventListener(AgendaEventListener listener) {
        AgendaEventListenerWrapper wrapper = new AgendaEventListenerWrapper( listener );
        this.session.addEventListener( wrapper );
    }

    public Collection<AgendaEventListener> getAgendaEventListeners() {
        // TODO incompatible with the javadoc of the implemented method which states "Returns all event listeners"
        List<AgendaEventListener> listeners = new ArrayList<AgendaEventListener>();
        for ( Object listener : this.session.getAgendaEventListeners() ) {
            if ( listener instanceof AgendaEventListenerWrapper ) {
                listeners.add( ((AgendaEventListenerWrapper) listener).unWrap() );
            } else if (listener instanceof AgendaEventListener) {
                listeners.add( (AgendaEventListener) listener );
            }
        }
        return Collections.unmodifiableCollection( listeners );
    }

    public void removeEventListener(AgendaEventListener listener) {
        AgendaEventListenerWrapper wrapper;
        if ( listener != null && !(listener instanceof AgendaEventListenerWrapper) ) {
            wrapper = new AgendaEventListenerWrapper( listener );
        } else {
            wrapper = (AgendaEventListenerWrapper) listener;
        }
        this.session.removeEventListener( wrapper );
    }

    private InternalProcessRuntime getInternalProcessRuntime() {
        InternalProcessRuntime processRuntime = this.session.getProcessRuntime();
        if ( processRuntime == null ) throw new RuntimeException( "There is no ProcessRuntime available: are jBPM libraries missing on classpath?" );
        return processRuntime;
    }

    public void addEventListener(ProcessEventListener listener) {
        getInternalProcessRuntime().addEventListener( listener );
    }

    public Collection<ProcessEventListener> getProcessEventListeners() {
        return getInternalProcessRuntime().getProcessEventListeners();
    }

    public void removeEventListener(ProcessEventListener listener) {
        getInternalProcessRuntime().removeEventListener( listener );
    }

    public KnowledgeBase getKieBase() {
        if ( this.kbase == null ) {
            this.kbase = new KnowledgeBaseImpl( session.getRuleBase() );
        }
        return this.kbase;
    }

    public int fireAllRules() {
        return this.session.fireAllRules();
    }

    public int fireAllRules(int max) {
        return this.session.fireAllRules( max );
    }

    public int fireAllRules(AgendaFilter agendaFilter) {
        return this.session.fireAllRules( new AgendaFilterWrapper( agendaFilter ) );
    }

    public int fireAllRules(AgendaFilter agendaFilter,
                            int max) {
        return this.session.fireAllRules( new AgendaFilterWrapper( agendaFilter ), max );
    }

    public void fireUntilHalt() {
        this.session.fireUntilHalt();
    }

    public void fireUntilHalt(AgendaFilter agendaFilter) {
        this.session.fireUntilHalt( new AgendaFilterWrapper( agendaFilter ) );
    }

    @SuppressWarnings("unchecked")
    public <T extends SessionClock> T getSessionClock() {
        return (T) this.session.getSessionClock();
    }

    public void halt() {
        this.session.halt();
    }

    public void dispose() {
        if (logger != null) {
            try {
                logger.close();
            } catch (Exception e) { /* the logger was already closed, swallow */ }
        }
        this.session.dispose();
        this.session = DisposedReteooWorkingMemory.INSTANCE;
    }

    public boolean isAlive() {
        return this.session != DisposedReteooWorkingMemory.INSTANCE;
    }

    public void destroy() {
        dispose();
    }

    public FactHandle insert(Object object) {
        return this.session.insert( object );
    }

    public void retract(FactHandle factHandle) {
        this.session.delete( factHandle );
    }

    public void delete(FactHandle factHandle) {
        this.session.delete( factHandle );
    }

    public void update(FactHandle factHandle) {
        this.session.update( factHandle,
                             ((InternalFactHandle) factHandle).getObject() );
    }

    public void update(FactHandle factHandle,
                       Object object) {
        this.session.update( factHandle,
                             object );
    }

    public FactHandle getFactHandle(Object object) {
        return this.session.getFactHandle( object );
    }

    public Object getObject(FactHandle factHandle) {
        return this.session.getObject( factHandle );
    }

    public ProcessInstance getProcessInstance(long id) {
        return this.session.getProcessInstance( id );
    }

    public ProcessInstance getProcessInstance(long id, boolean readOnly) {
        return this.session.getProcessInstance( id, readOnly );
    }

    public void abortProcessInstance(long id) {
        this.session.getProcessRuntime().abortProcessInstance( id );
    }

    public Collection<ProcessInstance> getProcessInstances() {
        List<ProcessInstance> result = new ArrayList<ProcessInstance>();
        result.addAll( this.session.getProcessInstances() );
        return result;
    }

    public WorkItemManager getWorkItemManager() {
        return this.session.getWorkItemManager();
    }

    public ProcessInstance startProcess(String processId) {
        return this.session.startProcess( processId );
    }

    public ProcessInstance startProcess(String processId,
                                        Map<String, Object> parameters) {
        return this.session.startProcess( processId,
                                          parameters );
    }

    public ProcessInstance createProcessInstance(String processId,
                                                 Map<String, Object> parameters) {
        return this.session.createProcessInstance( processId, parameters );
    }

    public ProcessInstance startProcessInstance(long processInstanceId) {
        return this.session.startProcessInstance( processInstanceId );
    }

    public void signalEvent(String type,
                            Object event) {
        this.session.getProcessRuntime().signalEvent( type, event );
    }

    public void signalEvent(String type,
                            Object event,
                            long processInstanceId) {
        this.session.getProcessRuntime().signalEvent( type, event, processInstanceId );
    }

    public void setGlobal(String identifier,
                          Object object) {
        this.session.setGlobal( identifier,
                                object );
    }

    public Object getGlobal(String identifier) {
        return this.session.getGlobal( identifier );
    }

    public Globals getGlobals() {
        return (Globals) this.session.getGlobalResolver();
    }

    public Calendars getCalendars() {
        return this.session.getCalendars();
    }

    public Environment getEnvironment() {
        return this.session.getEnvironment();
    }

    //    public Future<Object> asyncInsert(Object object) {
    //        return new FutureAdapter( this.session.asyncInsert( object ) );
    //    }
    //
    //    public Future<Object> asyncInsert(Object[] array) {
    //        return new FutureAdapter( this.session.asyncInsert( array ) );
    //    }
    //
    //    public Future<Object> asyncInsert(Iterable< ? > iterable) {
    //        return new FutureAdapter( this.session.asyncInsert( iterable ) );
    //    }
    //
    //    public Future< ? > asyncFireAllRules() {
    //        return new FutureAdapter( this.session.asyncFireAllRules() );
    //    }

    public <T extends org.kie.api.runtime.rule.FactHandle> Collection<T> getFactHandles() {
        return new ObjectStoreWrapper( session.getObjectStore(),
                                       null,
                                       ObjectStoreWrapper.FACT_HANDLE );
    }

    public <T extends org.kie.api.runtime.rule.FactHandle> Collection<T> getFactHandles(org.kie.api.runtime.ObjectFilter filter) {
        return new ObjectStoreWrapper( session.getObjectStore(),
                                       filter,
                                       ObjectStoreWrapper.FACT_HANDLE );
    }

    public Collection<? extends Object> getObjects() {
        return new ObjectStoreWrapper( session.getObjectStore(),
                                       null,
                                       ObjectStoreWrapper.OBJECT );
    }

    public Collection<? extends Object> getObjects(org.kie.api.runtime.ObjectFilter filter) {
        return new ObjectStoreWrapper( session.getObjectStore(),
                                       filter,
                                       ObjectStoreWrapper.OBJECT );
    }

    public void delete(org.drools.core.FactHandle factHandle,
                        Rule rule,
                        Activation activation) throws FactException {
        this.session.delete( factHandle,
                              rule,
                              activation );
    }

    public void update(FactHandle factHandle,
                       Object object,
                       long mask,
                       Class<?> modifiedClass,
                       Activation activation) throws FactException {
        this.session.update( (org.drools.core.FactHandle) factHandle,
                             object,
                             mask,
                             modifiedClass,
                             activation );
    }

    public EntryPointId getEntryPoint() {
        return session.getEntryPoint();
    }

    public InternalWorkingMemory getInternalWorkingMemory() {
        return session;
    }

    public org.drools.core.FactHandle getFactHandleByIdentity(Object object) {
        return session.getFactHandleByIdentity( object );
    }

    public static abstract class AbstractImmutableCollection
            implements
            Collection {

        public boolean add(Object o) {
            throw new UnsupportedOperationException( "This is an immmutable Collection" );
        }

        public boolean addAll(Collection c) {
            throw new UnsupportedOperationException( "This is an immmutable Collection" );
        }

        public void clear() {
            throw new UnsupportedOperationException( "This is an immmutable Collection" );
        }

        public boolean remove(Object o) {
            throw new UnsupportedOperationException( "This is an immmutable Collection" );
        }

        public boolean removeAll(Collection c) {
            throw new UnsupportedOperationException( "This is an immmutable Collection" );
        }

        public boolean retainAll(Collection c) {
            throw new UnsupportedOperationException( "This is an immmutable Collection" );
        }
    }

    public static class ObjectStoreWrapper extends AbstractImmutableCollection {
        public ObjectStore                     store;
        public org.kie.api.runtime.ObjectFilter filter;
        public int                             type;           // 0 == object, 1 == facthandle
        public static final int                OBJECT      = 0;
        public static final int                FACT_HANDLE = 1;

        public ObjectStoreWrapper(ObjectStore store,
                                  org.kie.api.runtime.ObjectFilter filter,
                                  int type) {
            this.store = store;
            this.filter = filter;
            this.type = type;
        }

        public boolean contains(Object object) {
            if ( object instanceof FactHandle ) {
                return this.store.getObjectForHandle( (InternalFactHandle) object ) != null;
            } else {
                return this.store.getHandleForObject( object ) != null;
            }
        }

        public boolean containsAll(Collection c) {
            for ( Object object : c ) {
                if ( !contains( object ) ) {
                    return false;
                }
            }
            return true;
        }

        public boolean isEmpty() {
            if ( this.filter == null ) {
                return this.store.isEmpty();
            }

            return size() == 0;
        }

        public int size() {
            if ( this.filter == null ) {
                return this.store.size();
            }

            int i = 0;
            for (Object o : this) {
                i++;
            }

            return i;
        }

        public Iterator< ? > iterator() {
            Iterator it;
            if ( type == OBJECT ) {
                if ( filter != null ) {
                    it = store.iterateObjects( filter );
                } else {
                    it = store.iterateObjects();
                }
            } else {
                if ( filter != null ) {
                    it = store.iterateFactHandles( filter );
                } else {
                    it = store.iterateFactHandles();
                }
            }
            return it;
        }

        public Object[] toArray() {
            if ( type == FACT_HANDLE ) {
                return toArray( new InternalFactHandle[size()] );
            } else {
                return toArray( new Object[size()] );
            }

        }

        public Object[] toArray(Object[] array) {
            if ( array == null || array.length != size() ) {
                if ( type == FACT_HANDLE ) {
                    array = new InternalFactHandle[size()];
                } else {
                    array = new Object[size()];
                }
            }

            int i = 0;
            for (Object o : this) {
                array[i++] = o;
            }

            return array;
        }
    }

    public static class WorkingMemoryEventListenerWrapper
            implements
            org.drools.core.event.WorkingMemoryEventListener {
        private final WorkingMemoryEventListener listener;

        public WorkingMemoryEventListenerWrapper(WorkingMemoryEventListener listener) {
            this.listener = listener;
        }

        public void objectInserted(ObjectInsertedEvent event) {
            listener.objectInserted( new ObjectInsertedEventImpl( event ) );
        }

        public void objectRetracted(ObjectRetractedEvent event) {
            listener.objectDeleted(new ObjectDeletedEventImpl(event));
        }

        public void objectUpdated(ObjectUpdatedEvent event) {
            listener.objectUpdated( new ObjectUpdatedEventImpl( event ) );
        }

        public WorkingMemoryEventListener unWrap() {
            return listener;
        }

        /**
         * Since this is a class adapter for API compatibility, the 
         * equals() and hashCode() methods simply delegate the calls 
         * to the wrapped instance. That is implemented this way
         * in order for them to be able to match corresponding instances
         * in internal hash-based maps and sets.  
         */
        @Override
        public int hashCode() {
            return listener != null ? listener.hashCode() : 0;
        }

        /**
         * Since this is a class adapter for API compatibility, the 
         * equals() and hashCode() methods simply delegate the calls 
         * to the wrapped instance. That is implemented this way
         * in order for them to be able to match corresponding instances
         * in internal hash-based maps and sets.  
         */
        @Override
        public boolean equals(Object obj) {
            if ( listener == null || obj == null ) {
                return obj == listener;
            }
            if ( obj instanceof WorkingMemoryEventListenerWrapper ) {
                return this.listener.equals( ((WorkingMemoryEventListenerWrapper) obj).unWrap() );
            }
            return this.listener.equals( obj );
        }
    }

    public static class AgendaEventListenerWrapper
            implements
            org.drools.core.event.AgendaEventListener {
        private final AgendaEventListener listener;

        public AgendaEventListenerWrapper(AgendaEventListener listener) {
            this.listener = listener;
        }

        public void activationCancelled(ActivationCancelledEvent event,
                                        WorkingMemory workingMemory) {

            listener.matchCancelled(new ActivationCancelledEventImpl(event.getActivation(),
                    ((InternalWorkingMemory) workingMemory).getKnowledgeRuntime(),
                    event.getCause()));

        }

        public void activationCreated(ActivationCreatedEvent event,
                                      WorkingMemory workingMemory) {
            listener.matchCreated(new ActivationCreatedEventImpl(event.getActivation(),
                    ((InternalWorkingMemory) workingMemory).getKnowledgeRuntime()));
        }

        public void beforeActivationFired(BeforeActivationFiredEvent event,
                                          WorkingMemory workingMemory) {
            listener.beforeMatchFired(new BeforeActivationFiredEventImpl(event.getActivation(),
                    ((InternalWorkingMemory) workingMemory).getKnowledgeRuntime()));
        }

        public void afterActivationFired(AfterActivationFiredEvent event,
                                         WorkingMemory workingMemory) {
            listener.afterMatchFired(new AfterActivationFiredEventImpl(event.getActivation(),
                    ((InternalWorkingMemory) workingMemory).getKnowledgeRuntime()));
        }

        public void agendaGroupPopped(AgendaGroupPoppedEvent event,
                                      WorkingMemory workingMemory) {
            listener.agendaGroupPopped( new AgendaGroupPoppedEventImpl( event.getAgendaGroup(),
                                                                        ((InternalWorkingMemory) workingMemory).getKnowledgeRuntime() ) );
        }

        public void agendaGroupPushed(AgendaGroupPushedEvent event,
                                      WorkingMemory workingMemory) {
            listener.agendaGroupPushed( new AgendaGroupPushedEventImpl( event.getAgendaGroup(),
                                                                        ((InternalWorkingMemory) workingMemory).getKnowledgeRuntime() ) );
        }

        public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event,
                                                WorkingMemory workingMemory) {
            listener.afterRuleFlowGroupActivated( new RuleFlowGroupActivatedEventImpl( event.getRuleFlowGroup(),  
                                                                                       ((InternalWorkingMemory) workingMemory).getKnowledgeRuntime()  ) );
        }

        public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event,
                                                  WorkingMemory workingMemory) {
            listener.afterRuleFlowGroupDeactivated( new RuleFlowGroupDeactivatedEventImpl( event.getRuleFlowGroup(),  
                                                                                         ((InternalWorkingMemory) workingMemory).getKnowledgeRuntime()  ) );            
        }

        public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event,
                                                 WorkingMemory workingMemory) {
            listener.beforeRuleFlowGroupActivated( new RuleFlowGroupActivatedEventImpl( event.getRuleFlowGroup(),  
                                                                                       ((InternalWorkingMemory) workingMemory).getKnowledgeRuntime()  ) );            
        }

        public void beforeRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event,
                                                   WorkingMemory workingMemory) {
            listener.beforeRuleFlowGroupDeactivated( new RuleFlowGroupDeactivatedEventImpl( event.getRuleFlowGroup(),  
                                                                                           ((InternalWorkingMemory) workingMemory).getKnowledgeRuntime()  ) );            
        }

        public AgendaEventListener unWrap() {
            return listener;
        }

        /**
         * Since this is a class adapter for API compatibility, the 
         * equals() and hashCode() methods simply delegate the calls 
         * to the wrapped instance. That is implemented this way
         * in order for them to be able to match corresponding instances
         * in internal hash-based maps and sets.  
         */
        @Override
        public int hashCode() {
            return listener != null ? listener.hashCode() : 0;
        }

        /**
         * Since this is a class adapter for API compatibility, the 
         * equals() and hashCode() methods simply delegate the calls 
         * to the wrapped instance. That is implemented this way
         * in order for them to be able to match corresponding instances
         * in internal hash-based maps and sets.  
         */
        @Override
        public boolean equals(Object obj) {
            if ( listener == null || obj == null ) {
                return obj == listener;
            }
            if ( obj instanceof AgendaEventListenerWrapper ) {
                return this.listener.equals( ((AgendaEventListenerWrapper) obj).unWrap() );
            }
            return this.listener.equals( obj );
        }
    }

    public static class AgendaFilterWrapper
            implements
            org.drools.core.spi.AgendaFilter {
        private AgendaFilter filter;

        public AgendaFilterWrapper(AgendaFilter filter) {
            this.filter = filter;
        }

        public boolean accept(Activation activation) {
            return filter.accept( activation );
        }
    }

    public Agenda getAgenda() {
        return new AgendaImpl( (InternalAgenda) this.session.getAgenda() );
    }

    public void registerChannel(String name,
                                Channel channel) {
        this.session.registerChannel( name,
                                      channel );
    }

    public void unregisterChannel(String name) {
        this.session.unregisterChannel( name );
    }

    public Map<String, Channel> getChannels() {
        return this.session.getChannels();
    }

    public ObjectTypeConfigurationRegistry getObjectTypeConfigurationRegistry() {
        return this.session.getObjectTypeConfigurationRegistry();
    }

    public RuleBase getRuleBase() {
        return ((KnowledgeBaseImpl) this.kbase).ruleBase;
    }

    public QueryResults getQueryResults(String query,
                                        Object... arguments) {
        return new NativeQueryResults( this.session.getQueryResults( query,
                                                                     arguments ) );
    }

    public <T> T execute(Command<T> command) {
        return execute( null,
                        command );
    }

    public <T> T execute(Context context,
                         Command<T> command) {

        ExecutionResultImpl results = null;
        if ( context != null ) {
            results = (ExecutionResultImpl) ((KnowledgeCommandContext) context).getExecutionResults();
        }

        if ( results == null ) {
            results = new ExecutionResultImpl();
        }

        if ( !(command instanceof BatchExecutionCommandImpl) ) {
            return (T) ((GenericCommand) command).execute( new FixedKnowledgeCommandContext( context,
                                                                                             null,
                                                                                             this.kbase,
                                                                                             this,
                                                                                             results ) );
        }

        try {
            session.startBatchExecution( results );
            ((GenericCommand) command).execute( new FixedKnowledgeCommandContext( context,
                                                                                  null,
                                                                                  this.kbase,
                                                                                  this,
                                                                                  results ) );
            ExecutionResults result = session.getExecutionResult();
            return (T) result;
        } finally {
            session.endBatchExecution();
        }
    }

    public String getEntryPointId() {
        return this.session.getEntryPointId();
    }

    public long getFactCount() {
        return this.session.getFactCount();
    }

    public LiveQuery openLiveQuery(String query,
                                   Object[] arguments,
                                   ViewChangedEventListener listener) {
        return this.session.openLiveQuery( query,
                                           arguments,
                                           listener );
    }

    public KieSessionConfiguration getSessionConfiguration() {
        return this.session.getSessionConfiguration();
    }

    public TimedRuleExecutionFilter getTimedRuleExecutionFilter() {
        return this.session.getTimedRuleExecutionFilter();
    }

    public void setTimedRuleExecutionFilter(TimedRuleExecutionFilter timedRuleExecutionFilter) {
        this.session.setTimedRuleExecutionFilter(timedRuleExecutionFilter);
    }

    public TimerService getTimerService() {
        return this.session.getTimerService();
    }

    public void startOperation() {
        this.session.startOperation();
    }

    public void endOperation() {
        this.session.endOperation();
    }

    public void executeQueuedActions() {
        this.session.executeQueuedActions();
    }

    public Queue<WorkingMemoryAction> getActionQueue() {
        return this.session.getActionQueue();
    }

    public InternalProcessRuntime getProcessRuntime() {
        return this.session.getProcessRuntime();
    }

    public void queueWorkingMemoryAction(WorkingMemoryAction action) {
        this.session.queueWorkingMemoryAction( action );
    }

    public void setId(int id) {
        this.session.setId( id );
    }

    public void setEndOperationListener(EndOperationListener listener) {
        this.session.setEndOperationListener( listener );
    }

    public long getLastIdleTimestamp() {
        return this.session.getLastIdleTimestamp();
    }

    @Override
    public ProcessInstance startProcess(String processId,
            CorrelationKey correlationKey, Map<String, Object> parameters) {

        return ((CorrelationAwareProcessRuntime)this.session).startProcess(processId, correlationKey, parameters);
    }

    @Override
    public ProcessInstance createProcessInstance(String processId,
            CorrelationKey correlationKey, Map<String, Object> parameters) {
        
        return ((CorrelationAwareProcessRuntime)this.session).createProcessInstance(processId, correlationKey, parameters);
    }

    @Override
    public ProcessInstance getProcessInstance(CorrelationKey correlationKey) {

        return ((CorrelationAwareProcessRuntime)this.session).getProcessInstance(correlationKey);
    }

}
