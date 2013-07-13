package com.uprootlabs.trackme;

final class SessionBatchTuple {
    final private String sessionID;
    final private int batchID;

    public SessionBatchTuple(final String sessionID, final int batchID) {
      this.sessionID = sessionID;
      this.batchID = batchID;
    }

    public String getSessionID() {
      return sessionID;
    }

    public int getBatchID() {
      return batchID;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public boolean equals(final Object obj) {
      try {
        final SessionBatchTuple that = (SessionBatchTuple) obj;
        return (that.sessionID.equals(sessionID) && that.batchID == batchID);
      } catch (final ClassCastException e) {
        e.printStackTrace();
        return false;
      }
    }

  }