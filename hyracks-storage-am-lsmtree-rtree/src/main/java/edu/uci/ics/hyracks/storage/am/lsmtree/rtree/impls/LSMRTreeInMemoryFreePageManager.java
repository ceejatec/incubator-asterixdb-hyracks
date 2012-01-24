package edu.uci.ics.hyracks.storage.am.lsmtree.rtree.impls;

import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexMetaDataFrame;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexMetaDataFrameFactory;
import edu.uci.ics.hyracks.storage.am.lsmtree.common.freepage.InMemoryFreePageManager;

public class LSMRTreeInMemoryFreePageManager extends InMemoryFreePageManager {

    public LSMRTreeInMemoryFreePageManager(int maxCapacity, ITreeIndexMetaDataFrameFactory metaDataFrameFactory) {
        super(maxCapacity, metaDataFrameFactory);
        currentCapacity = 3;
    }

    @Override
    public void init(ITreeIndexMetaDataFrame metaFrame, int currentMaxPage) throws HyracksDataException {
        currentCapacity = 3;
    }

    public void reset() {
        currentCapacity = 3;
    }
}