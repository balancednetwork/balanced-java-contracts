package score.impl;

import score.ArrayDB;
import score.BranchDB;
import score.DictDB;
import score.VarDB;

@SuppressWarnings("rawtypes")
public interface AnyDB extends VarDB, ArrayDB, DictDB, BranchDB {
}
