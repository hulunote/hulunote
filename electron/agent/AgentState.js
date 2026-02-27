class AgentState {
  constructor(initialData = {}) {
    this._data = { ...initialData };
  }

  get(key) {
    return this._data[key];
  }

  set(key, value) {
    this._data[key] = value;
    return this;
  }

  has(key) {
    return key in this._data;
  }

  toJSON() {
    return { ...this._data };
  }

  /**
   * Create an isolated copy with only the specified keys.
   * Used for sub-agent state isolation.
   */
  fork(keys = []) {
    const forked = {};
    for (const key of keys) {
      if (key in this._data) {
        forked[key] = JSON.parse(JSON.stringify(this._data[key]));
      }
    }
    return new AgentState(forked);
  }

  /**
   * Merge values from another AgentState for the specified keys.
   * Used to return shared state from a sub-agent.
   */
  mergeFrom(other, keys = []) {
    for (const key of keys) {
      if (other.has(key)) {
        this._data[key] = other.get(key);
      }
    }
    return this;
  }
}

module.exports = AgentState;
