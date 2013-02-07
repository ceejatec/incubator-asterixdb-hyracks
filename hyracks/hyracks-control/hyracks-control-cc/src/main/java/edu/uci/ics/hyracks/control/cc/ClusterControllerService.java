/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.control.cc;

import java.io.File;
import java.io.FileReader;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xml.sax.InputSource;

import edu.uci.ics.hyracks.api.client.ClusterControllerInfo;
import edu.uci.ics.hyracks.api.client.HyracksClientInterfaceFunctions;
import edu.uci.ics.hyracks.api.client.NodeControllerInfo;
import edu.uci.ics.hyracks.api.comm.NetworkAddress;
import edu.uci.ics.hyracks.api.context.ICCContext;
import edu.uci.ics.hyracks.api.dataset.DatasetDirectoryRecord;
import edu.uci.ics.hyracks.api.dataset.DatasetDirectoryRecord.Status;
import edu.uci.ics.hyracks.api.dataset.IDatasetDirectoryService;
import edu.uci.ics.hyracks.api.job.JobId;
import edu.uci.ics.hyracks.api.job.JobStatus;
import edu.uci.ics.hyracks.api.topology.ClusterTopology;
import edu.uci.ics.hyracks.api.topology.TopologyDefinitionParser;
import edu.uci.ics.hyracks.control.cc.application.CCApplicationContext;
import edu.uci.ics.hyracks.control.cc.dataset.DatasetDirectoryService;
import edu.uci.ics.hyracks.control.cc.job.JobRun;
import edu.uci.ics.hyracks.control.cc.web.WebServer;
import edu.uci.ics.hyracks.control.cc.work.ApplicationCreateWork;
import edu.uci.ics.hyracks.control.cc.work.ApplicationDestroyWork;
import edu.uci.ics.hyracks.control.cc.work.ApplicationMessageWork;
import edu.uci.ics.hyracks.control.cc.work.ApplicationStartWork;
import edu.uci.ics.hyracks.control.cc.work.ApplicationStateChangeWork;
import edu.uci.ics.hyracks.control.cc.work.GetDatasetDirectoryServiceInfoWork;
import edu.uci.ics.hyracks.control.cc.work.GetIpAddressNodeNameMapWork;
import edu.uci.ics.hyracks.control.cc.work.GetJobStatusWork;
import edu.uci.ics.hyracks.control.cc.work.GetNodeControllersInfoWork;
import edu.uci.ics.hyracks.control.cc.work.GetRecordDescriptorWork;
import edu.uci.ics.hyracks.control.cc.work.GetResultPartitionLocationsWork;
import edu.uci.ics.hyracks.control.cc.work.GetResultStatusWork;
import edu.uci.ics.hyracks.control.cc.work.JobStartWork;
import edu.uci.ics.hyracks.control.cc.work.JobletCleanupNotificationWork;
import edu.uci.ics.hyracks.control.cc.work.NodeHeartbeatWork;
import edu.uci.ics.hyracks.control.cc.work.RegisterNodeWork;
import edu.uci.ics.hyracks.control.cc.work.RegisterPartitionAvailibilityWork;
import edu.uci.ics.hyracks.control.cc.work.RegisterPartitionRequestWork;
import edu.uci.ics.hyracks.control.cc.work.RegisterResultPartitionLocationWork;
import edu.uci.ics.hyracks.control.cc.work.RemoveDeadNodesWork;
import edu.uci.ics.hyracks.control.cc.work.ReportProfilesWork;
import edu.uci.ics.hyracks.control.cc.work.ReportResultPartitionFailureWork;
import edu.uci.ics.hyracks.control.cc.work.ReportResultPartitionWriteCompletionWork;
import edu.uci.ics.hyracks.control.cc.work.TaskCompleteWork;
import edu.uci.ics.hyracks.control.cc.work.TaskFailureWork;
import edu.uci.ics.hyracks.control.cc.work.UnregisterNodeWork;
import edu.uci.ics.hyracks.control.cc.work.WaitForJobCompletionWork;
import edu.uci.ics.hyracks.control.common.AbstractRemoteService;
import edu.uci.ics.hyracks.control.common.context.ServerContext;
import edu.uci.ics.hyracks.control.common.controllers.CCConfig;
import edu.uci.ics.hyracks.control.common.ipc.CCNCFunctions;
import edu.uci.ics.hyracks.control.common.ipc.CCNCFunctions.Function;
import edu.uci.ics.hyracks.control.common.logs.LogFile;
import edu.uci.ics.hyracks.control.common.work.IPCResponder;
import edu.uci.ics.hyracks.control.common.work.IResultCallback;
import edu.uci.ics.hyracks.control.common.work.WorkQueue;
import edu.uci.ics.hyracks.ipc.api.IIPCHandle;
import edu.uci.ics.hyracks.ipc.api.IIPCI;
import edu.uci.ics.hyracks.ipc.exceptions.IPCException;
import edu.uci.ics.hyracks.ipc.impl.IPCSystem;
import edu.uci.ics.hyracks.ipc.impl.JavaSerializationBasedPayloadSerializerDeserializer;

public class ClusterControllerService extends AbstractRemoteService {
    private static Logger LOGGER = Logger.getLogger(ClusterControllerService.class.getName());

    private final CCConfig ccConfig;

    private IPCSystem clusterIPC;

    private IPCSystem clientIPC;

    private final LogFile jobLog;

    private final Map<String, NodeControllerState> nodeRegistry;

    private final Map<String, Set<String>> ipAddressNodeNameMap;

    private final Map<String, CCApplicationContext> applications;

    private final ServerContext serverCtx;

    private final WebServer webServer;

    private ClusterControllerInfo info;

    private final Map<JobId, JobRun> activeRunMap;

    private final Map<JobId, JobRun> runMapArchive;

    private final WorkQueue workQueue;

    private final ExecutorService executor;

    private final Timer timer;

    private final ICCContext ccContext;

    private final DeadNodeSweeper sweeper;

    private final IDatasetDirectoryService datasetDirectoryService;

    private long jobCounter;

    public ClusterControllerService(final CCConfig ccConfig) throws Exception {
        this.ccConfig = ccConfig;
        File jobLogFolder = new File(ccConfig.ccRoot, "logs/jobs");
        jobLog = new LogFile(jobLogFolder);
        nodeRegistry = new LinkedHashMap<String, NodeControllerState>();
        ipAddressNodeNameMap = new HashMap<String, Set<String>>();
        applications = new Hashtable<String, CCApplicationContext>();
        serverCtx = new ServerContext(ServerContext.ServerType.CLUSTER_CONTROLLER, new File(ccConfig.ccRoot));
        executor = Executors.newCachedThreadPool();
        IIPCI ccIPCI = new ClusterControllerIPCI();
        clusterIPC = new IPCSystem(new InetSocketAddress(ccConfig.clusterNetPort), ccIPCI,
                new CCNCFunctions.SerializerDeserializer());
        IIPCI ciIPCI = new HyracksClientInterfaceIPCI();
        clientIPC = new IPCSystem(new InetSocketAddress(ccConfig.clientNetIpAddress, ccConfig.clientNetPort), ciIPCI,
                new JavaSerializationBasedPayloadSerializerDeserializer());
        webServer = new WebServer(this);
        activeRunMap = new HashMap<JobId, JobRun>();
        runMapArchive = new LinkedHashMap<JobId, JobRun>() {
            private static final long serialVersionUID = 1L;

            protected boolean removeEldestEntry(Map.Entry<JobId, JobRun> eldest) {
                return size() > ccConfig.jobHistorySize;
            }
        };
        workQueue = new WorkQueue();
        this.timer = new Timer(true);
        final ClusterTopology topology = computeClusterTopology(ccConfig);
        ccContext = new ICCContext() {
            @Override
            public void getIPAddressNodeMap(Map<String, Set<String>> map) throws Exception {
                GetIpAddressNodeNameMapWork ginmw = new GetIpAddressNodeNameMapWork(ClusterControllerService.this, map);
                workQueue.scheduleAndSync(ginmw);
            }

            @Override
            public ClusterControllerInfo getClusterControllerInfo() {
                return info;
            }

            @Override
            public ClusterTopology getClusterTopology() {
                return topology;
            }
        };
        sweeper = new DeadNodeSweeper();
        datasetDirectoryService = new DatasetDirectoryService();
        jobCounter = 0;
    }

    private static ClusterTopology computeClusterTopology(CCConfig ccConfig) throws Exception {
        if (ccConfig.clusterTopologyDefinition == null) {
            return null;
        }
        FileReader fr = new FileReader(ccConfig.clusterTopologyDefinition);
        InputSource in = new InputSource(fr);
        try {
            return TopologyDefinitionParser.parse(in);
        } finally {
            fr.close();
        }
    }

    @Override
    public void start() throws Exception {
        LOGGER.log(Level.INFO, "Starting ClusterControllerService: " + this);
        clusterIPC.start();
        clientIPC.start();
        webServer.setPort(ccConfig.httpPort);
        webServer.start();
        workQueue.start();
        info = new ClusterControllerInfo(ccConfig.clientNetIpAddress, ccConfig.clientNetPort,
                webServer.getListeningPort());
        timer.schedule(sweeper, 0, ccConfig.heartbeatPeriod);
        jobLog.open();
        LOGGER.log(Level.INFO, "Started ClusterControllerService");
    }

    @Override
    public void stop() throws Exception {
        LOGGER.log(Level.INFO, "Stopping ClusterControllerService");
        executor.shutdownNow();
        webServer.stop();
        sweeper.cancel();
        workQueue.stop();
        jobLog.close();
        LOGGER.log(Level.INFO, "Stopped ClusterControllerService");
    }

    public ServerContext getServerContext() {
        return serverCtx;
    }

    public ICCContext getCCContext() {
        return ccContext;
    }

    public Map<String, CCApplicationContext> getApplicationMap() {
        return applications;
    }

    public Map<JobId, JobRun> getActiveRunMap() {
        return activeRunMap;
    }

    public Map<JobId, JobRun> getRunMapArchive() {
        return runMapArchive;
    }

    public Map<String, Set<String>> getIpAddressNodeNameMap() {
        return ipAddressNodeNameMap;
    }

    public LogFile getJobLogFile() {
        return jobLog;
    }

    public WorkQueue getWorkQueue() {
        return workQueue;
    }

    public Executor getExecutor() {
        return executor;
    }

    public Map<String, NodeControllerState> getNodeMap() {
        return nodeRegistry;
    }

    public CCConfig getConfig() {
        return ccConfig;
    }

    private JobId createJobId() {
        return new JobId(jobCounter++);
    }

    public ClusterControllerInfo getClusterControllerInfo() {
        return info;
    }

    public CCConfig getCCConfig() {
        return ccConfig;
    }

    public IPCSystem getClusterIPC() {
        return clusterIPC;
    }

    public NetworkAddress getDatasetDirectoryServiceInfo() {
        return new NetworkAddress(ccConfig.clientNetIpAddress.getBytes(), ccConfig.clientNetPort);
    }

    private class DeadNodeSweeper extends TimerTask {
        @Override
        public void run() {
            workQueue.schedule(new RemoveDeadNodesWork(ClusterControllerService.this));
        }
    }

    public IDatasetDirectoryService getDatasetDirectoryService() {
        return datasetDirectoryService;
    }

    private class HyracksClientInterfaceIPCI implements IIPCI {
        @Override
        public void deliverIncomingMessage(IIPCHandle handle, long mid, long rmid, Object payload, Exception exception) {
            HyracksClientInterfaceFunctions.Function fn = (HyracksClientInterfaceFunctions.Function) payload;
            switch (fn.getFunctionId()) {
                case GET_CLUSTER_CONTROLLER_INFO: {
                    try {
                        handle.send(mid, info, null);
                    } catch (IPCException e) {
                        e.printStackTrace();
                    }
                    return;
                }

                case CREATE_APPLICATION: {
                    HyracksClientInterfaceFunctions.CreateApplicationFunction caf = (HyracksClientInterfaceFunctions.CreateApplicationFunction) fn;
                    workQueue.schedule(new ApplicationCreateWork(ClusterControllerService.this, caf.getAppName(),
                            new IPCResponder<Object>(handle, mid)));
                    return;
                }

                case START_APPLICATION: {
                    HyracksClientInterfaceFunctions.StartApplicationFunction saf = (HyracksClientInterfaceFunctions.StartApplicationFunction) fn;
                    workQueue.schedule(new ApplicationStartWork(ClusterControllerService.this, saf.getAppName(),
                            new IPCResponder<Object>(handle, mid)));
                    return;
                }

                case DESTROY_APPLICATION: {
                    HyracksClientInterfaceFunctions.DestroyApplicationFunction daf = (HyracksClientInterfaceFunctions.DestroyApplicationFunction) fn;
                    workQueue.schedule(new ApplicationDestroyWork(ClusterControllerService.this, daf.getAppName(),
                            new IPCResponder<Object>(handle, mid)));
                    return;
                }

                case GET_JOB_STATUS: {
                    HyracksClientInterfaceFunctions.GetJobStatusFunction gjsf = (HyracksClientInterfaceFunctions.GetJobStatusFunction) fn;
                    workQueue.schedule(new GetJobStatusWork(ClusterControllerService.this, gjsf.getJobId(),
                            new IPCResponder<JobStatus>(handle, mid)));
                    return;
                }

                case START_JOB: {
                    HyracksClientInterfaceFunctions.StartJobFunction sjf = (HyracksClientInterfaceFunctions.StartJobFunction) fn;
                    JobId jobId = createJobId();
                    workQueue.schedule(new JobStartWork(ClusterControllerService.this, sjf.getAppName(), sjf
                            .getACGGFBytes(), sjf.getJobFlags(), jobId, new IPCResponder<JobId>(handle, mid)));
                    return;
                }

                case GET_DATASET_DIRECTORY_SERIVICE_INFO: {
                    workQueue.schedule(new GetDatasetDirectoryServiceInfoWork(ClusterControllerService.this,
                            new IPCResponder<NetworkAddress>(handle, mid)));
                    return;
                }

                case GET_DATASET_RESULT_STATUS: {
                    HyracksClientInterfaceFunctions.GetDatasetResultStatusFunction gdrlf = (HyracksClientInterfaceFunctions.GetDatasetResultStatusFunction) fn;
                    workQueue.schedule(new GetResultStatusWork(ClusterControllerService.this, gdrlf.getJobId(), gdrlf
                            .getResultSetId(), new IPCResponder<Status>(handle, mid)));
                    return;
                }

                case GET_DATASET_RECORD_DESCRIPTOR: {
                    HyracksClientInterfaceFunctions.GetDatasetRecordDescriptorFunction gdrdf = (HyracksClientInterfaceFunctions.GetDatasetRecordDescriptorFunction) fn;
                    workQueue.schedule(new GetRecordDescriptorWork(ClusterControllerService.this, gdrdf.getJobId(),
                            gdrdf.getResultSetId(), new IPCResponder<byte[]>(handle, mid)));
                    return;
                }

                case GET_DATASET_RESULT_LOCATIONS: {
                    HyracksClientInterfaceFunctions.GetDatasetResultLocationsFunction gdrlf = (HyracksClientInterfaceFunctions.GetDatasetResultLocationsFunction) fn;
                    workQueue.schedule(new GetResultPartitionLocationsWork(ClusterControllerService.this, gdrlf
                            .getJobId(), gdrlf.getResultSetId(), gdrlf.getKnownRecords(),
                            new IPCResponder<DatasetDirectoryRecord[]>(handle, mid)));
                    return;
                }

                case WAIT_FOR_COMPLETION: {
                    HyracksClientInterfaceFunctions.WaitForCompletionFunction wfcf = (HyracksClientInterfaceFunctions.WaitForCompletionFunction) fn;
                    workQueue.schedule(new WaitForJobCompletionWork(ClusterControllerService.this, wfcf.getJobId(),
                            new IPCResponder<Object>(handle, mid)));
                    return;
                }

                case GET_NODE_CONTROLLERS_INFO: {
                    workQueue.schedule(new GetNodeControllersInfoWork(ClusterControllerService.this,
                            new IPCResponder<Map<String, NodeControllerInfo>>(handle, mid)));
                    return;
                }

                case GET_CLUSTER_TOPOLOGY: {
                    try {
                        handle.send(mid, ccContext.getClusterTopology(), null);
                    } catch (IPCException e) {
                        e.printStackTrace();
                    }
                    return;
                }
            }
            try {
                handle.send(mid, null, new IllegalArgumentException("Unknown function " + fn.getFunctionId()));
            } catch (IPCException e) {
                e.printStackTrace();
            }
        }
    }

    private class ClusterControllerIPCI implements IIPCI {
        @Override
        public void deliverIncomingMessage(final IIPCHandle handle, long mid, long rmid, Object payload,
                Exception exception) {
            CCNCFunctions.Function fn = (Function) payload;
            switch (fn.getFunctionId()) {
                case REGISTER_NODE: {
                    CCNCFunctions.RegisterNodeFunction rnf = (CCNCFunctions.RegisterNodeFunction) fn;
                    workQueue.schedule(new RegisterNodeWork(ClusterControllerService.this, rnf.getNodeRegistration()));
                    return;
                }

                case UNREGISTER_NODE: {
                    CCNCFunctions.UnregisterNodeFunction unf = (CCNCFunctions.UnregisterNodeFunction) fn;
                    workQueue.schedule(new UnregisterNodeWork(ClusterControllerService.this, unf.getNodeId()));
                    return;
                }

                case NODE_HEARTBEAT: {
                    CCNCFunctions.NodeHeartbeatFunction nhf = (CCNCFunctions.NodeHeartbeatFunction) fn;
                    workQueue.schedule(new NodeHeartbeatWork(ClusterControllerService.this, nhf.getNodeId(), nhf
                            .getHeartbeatData()));
                    return;
                }

                case NOTIFY_JOBLET_CLEANUP: {
                    CCNCFunctions.NotifyJobletCleanupFunction njcf = (CCNCFunctions.NotifyJobletCleanupFunction) fn;
                    workQueue.schedule(new JobletCleanupNotificationWork(ClusterControllerService.this,
                            njcf.getJobId(), njcf.getNodeId()));
                    return;
                }

                case REPORT_PROFILE: {
                    CCNCFunctions.ReportProfileFunction rpf = (CCNCFunctions.ReportProfileFunction) fn;
                    workQueue.schedule(new ReportProfilesWork(ClusterControllerService.this, rpf.getProfiles()));
                    return;
                }

                case NOTIFY_TASK_COMPLETE: {
                    CCNCFunctions.NotifyTaskCompleteFunction ntcf = (CCNCFunctions.NotifyTaskCompleteFunction) fn;
                    workQueue.schedule(new TaskCompleteWork(ClusterControllerService.this, ntcf.getJobId(), ntcf
                            .getTaskId(), ntcf.getNodeId(), ntcf.getStatistics()));
                    return;
                }
                case NOTIFY_TASK_FAILURE: {
                    CCNCFunctions.NotifyTaskFailureFunction ntff = (CCNCFunctions.NotifyTaskFailureFunction) fn;
                    workQueue.schedule(new TaskFailureWork(ClusterControllerService.this, ntff.getJobId(), ntff
                            .getTaskId(), ntff.getDetails(), ntff.getDetails()));
                    return;
                }

                case REGISTER_PARTITION_PROVIDER: {
                    CCNCFunctions.RegisterPartitionProviderFunction rppf = (CCNCFunctions.RegisterPartitionProviderFunction) fn;
                    workQueue.schedule(new RegisterPartitionAvailibilityWork(ClusterControllerService.this, rppf
                            .getPartitionDescriptor()));
                    return;
                }

                case REGISTER_PARTITION_REQUEST: {
                    CCNCFunctions.RegisterPartitionRequestFunction rprf = (CCNCFunctions.RegisterPartitionRequestFunction) fn;
                    workQueue.schedule(new RegisterPartitionRequestWork(ClusterControllerService.this, rprf
                            .getPartitionRequest()));
                    return;
                }

                case REGISTER_RESULT_PARTITION_LOCATION: {
                    CCNCFunctions.RegisterResultPartitionLocationFunction rrplf = (CCNCFunctions.RegisterResultPartitionLocationFunction) fn;
                    workQueue.schedule(new RegisterResultPartitionLocationWork(ClusterControllerService.this, rrplf
                            .getJobId(), rrplf.getResultSetId(), rrplf.getOrderedResult(), rrplf
                            .getSerializedRecordDescriptor(), rrplf.getPartition(), rrplf.getNPartitions(), rrplf
                            .getNetworkAddress()));
                    return;
                }

                case REPORT_RESULT_PARTITION_WRITE_COMPLETION: {
                    CCNCFunctions.ReportResultPartitionWriteCompletionFunction rrplf = (CCNCFunctions.ReportResultPartitionWriteCompletionFunction) fn;
                    workQueue.schedule(new ReportResultPartitionWriteCompletionWork(ClusterControllerService.this,
                            rrplf.getJobId(), rrplf.getResultSetId(), rrplf.getPartition()));
                    return;
                }

                case REPORT_RESULT_PARTITION_FAILURE: {
                    CCNCFunctions.ReportResultPartitionFailureFunction rrplf = (CCNCFunctions.ReportResultPartitionFailureFunction) fn;
                    workQueue.schedule(new ReportResultPartitionFailureWork(ClusterControllerService.this, rrplf
                            .getJobId(), rrplf.getResultSetId(), rrplf.getPartition()));
                    return;
                }

                case APPLICATION_STATE_CHANGE_RESPONSE: {
                    CCNCFunctions.ApplicationStateChangeResponseFunction astrf = (CCNCFunctions.ApplicationStateChangeResponseFunction) fn;
                    workQueue.schedule(new ApplicationStateChangeWork(ClusterControllerService.this, astrf));
                    return;
                }
                case SEND_APPLICATION_MESSAGE: {
                    CCNCFunctions.SendApplicationMessageFunction rsf = (CCNCFunctions.SendApplicationMessageFunction) fn;
                    workQueue.schedule(new ApplicationMessageWork(ClusterControllerService.this, rsf.getMessage(), rsf
                            .getAppName(), rsf.getNodeId()));
                    return;
                }

                case GET_NODE_CONTROLLERS_INFO: {
                    workQueue.schedule(new GetNodeControllersInfoWork(ClusterControllerService.this,
                            new IResultCallback<Map<String, NodeControllerInfo>>() {
                                @Override
                                public void setValue(Map<String, NodeControllerInfo> result) {
                                    new IPCResponder<CCNCFunctions.GetNodeControllersInfoResponseFunction>(handle, -1)
                                            .setValue(new CCNCFunctions.GetNodeControllersInfoResponseFunction(result));
                                }

                                @Override
                                public void setException(Exception e) {

                                }
                            }));
                    return;
                }
            }
            LOGGER.warning("Unknown function: " + fn.getFunctionId());
        }
    }
}