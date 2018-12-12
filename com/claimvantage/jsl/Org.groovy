package com.claimvantage.jsl;

class Org implements Serializable {
    
    // This value is set
    // E.g. "config/project-scratch-def.encryption.json"
    String projectScratchDefPath
  
    // Filled in by scratch org creation e.g.
    // "test-drgwjqh3xsn0@example.com"
    // "g5$nPgf4!sdf<2"
    // "https://ability-saas-8856-dev-ed.lightning.force.com"
    String username
    String password
    String instanceUrl
    
    // Make it easy to see org name in messages such as stage messages
    String toString() {
        return getName();
    }
    
    // Extracted from projectScratchDefPath e.g. "encryption"
    String getName() {
        def parts = projectScratchDefPath.split('\\.')
        return parts.length > 2 ? parts[parts.length - 2] : null
    }
}