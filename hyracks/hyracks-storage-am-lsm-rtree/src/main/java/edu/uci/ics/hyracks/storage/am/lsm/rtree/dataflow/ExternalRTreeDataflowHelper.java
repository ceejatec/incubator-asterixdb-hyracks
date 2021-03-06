/*
 * Copyright 2009-2013 by The Regents of the University of California
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
package edu.uci.ics.hyracks.storage.am.lsm.rtree.dataflow;

import java.util.List;

import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ILinearizeComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ITypeTraits;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.storage.am.common.api.IIndex;
import edu.uci.ics.hyracks.storage.am.common.api.IPrimitiveValueProviderFactory;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndex;
import edu.uci.ics.hyracks.storage.am.common.api.TreeIndexException;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IIndexOperatorDescriptor;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMIOOperationCallbackFactory;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMIOOperationScheduler;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMMergePolicy;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMOperationTracker;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.ILSMOperationTrackerProvider;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.IVirtualBufferCache;
import edu.uci.ics.hyracks.storage.am.lsm.rtree.utils.LSMRTreeUtils;
import edu.uci.ics.hyracks.storage.am.rtree.frames.RTreePolicyType;
import edu.uci.ics.hyracks.storage.common.buffercache.IBufferCache;
import edu.uci.ics.hyracks.storage.common.file.IFileMapProvider;

public class ExternalRTreeDataflowHelper extends LSMRTreeDataflowHelper {

    private final int version;

    public ExternalRTreeDataflowHelper(IIndexOperatorDescriptor opDesc, IHyracksTaskContext ctx, int partition,
            IBinaryComparatorFactory[] btreeComparatorFactories,
            IPrimitiveValueProviderFactory[] valueProviderFactories, RTreePolicyType rtreePolicyType,
            ILSMMergePolicy mergePolicy, ILSMOperationTrackerProvider opTrackerFactory,
            ILSMIOOperationScheduler ioScheduler, ILSMIOOperationCallbackFactory ioOpCallbackFactory,
            ILinearizeComparatorFactory linearizeCmpFactory, int[] btreeFields, int version, boolean durable) {
        super(opDesc, ctx, partition, null, btreeComparatorFactories, valueProviderFactories, rtreePolicyType,
                mergePolicy, opTrackerFactory, ioScheduler, ioOpCallbackFactory, linearizeCmpFactory, null,
                btreeFields, null, null, null, durable);
        this.version = version;
    }

    public ExternalRTreeDataflowHelper(IIndexOperatorDescriptor opDesc, IHyracksTaskContext ctx, int partition,
            double bloomFilterFalsePositiveRate, IBinaryComparatorFactory[] btreeComparatorFactories,
            IPrimitiveValueProviderFactory[] valueProviderFactories, RTreePolicyType rtreePolicyType,
            ILSMMergePolicy mergePolicy, ILSMOperationTrackerProvider opTrackerFactory,
            ILSMIOOperationScheduler ioScheduler, ILSMIOOperationCallbackFactory ioOpCallbackFactory,
            ILinearizeComparatorFactory linearizeCmpFactory, int[] btreeFields, int version, boolean durable) {
        super(opDesc, ctx, partition, null, bloomFilterFalsePositiveRate, btreeComparatorFactories,
                valueProviderFactories, rtreePolicyType, mergePolicy, opTrackerFactory, ioScheduler,
                ioOpCallbackFactory, linearizeCmpFactory, null, btreeFields, null, null, null, durable);
        this.version = version;
    }

    @Override
    public IIndex getIndexInstance() {
        if (index != null)
            return index;
        synchronized (lcManager) {
            long resourceID;
            try {
                resourceID = getResourceID();
            } catch (HyracksDataException e) {
                return null;
            }
            try {
                index = lcManager.getIndex(resourceID);
            } catch (HyracksDataException e) {
                return null;
            }
        }
        return index;
    }

    @Override
    protected ITreeIndex createLSMTree(List<IVirtualBufferCache> virtualBufferCaches, FileReference file,
            IBufferCache diskBufferCache, IFileMapProvider diskFileMapProvider, ITypeTraits[] typeTraits,
            IBinaryComparatorFactory[] rtreeCmpFactories, IBinaryComparatorFactory[] btreeCmpFactories,
            ILSMOperationTracker opTracker, IPrimitiveValueProviderFactory[] valueProviderFactories,
            RTreePolicyType rtreePolicyType, ILinearizeComparatorFactory linearizeCmpFactory, int[] rtreeFields,
            ITypeTraits[] filterTypeTraits, IBinaryComparatorFactory[] filterCmpFactories, int[] filterFields)
            throws HyracksDataException {
        try {
            return LSMRTreeUtils.createExternalRTree(file, diskBufferCache, diskFileMapProvider, typeTraits,
                    rtreeCmpFactories, btreeCmpFactories, valueProviderFactories, rtreePolicyType,
                    bloomFilterFalsePositiveRate, mergePolicy, opTracker, ioScheduler,
                    ioOpCallbackFactory.createIOOperationCallback(), linearizeCmpFactory, btreeFields, version,
                    durable);
        } catch (TreeIndexException e) {
            throw new HyracksDataException(e);
        }
    }

    public int getTargetVersion() {
        return version;
    }

}
