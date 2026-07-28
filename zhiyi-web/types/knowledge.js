/**
 * @typedef {Object} FactBlock
 * @property {string} type - observation | decision | constraint | rule | evidence | action | outcome
 * @property {string} text
 * @property {Object} [metadata]
 */

/**
 * @typedef {Object} Artifact
 * @property {string} artifactType - commit | pr | issue | conversation | ...
 * @property {string} artifactRole - origin | evidence | attachment | reference
 * @property {string} [artifactUrl]
 * @property {string} [contentRef]
 */

/**
 * @typedef {Object} Knowledge
 * @property {number} id
 * @property {string} title
 * @property {string} knowledgeType
 * @property {string} [project]
 * @property {string} [module]
 * @property {string} [repository]
 * @property {string} [language]
 * @property {string} [framework]
 * @property {number} lifecycleStatus
 * @property {number} recallCount
 * @property {string[]} [tags]
 * @property {FactBlock[]} [facts]
 * @property {Artifact[]} [artifacts]
 * @property {string} [updateTime]
 */

/**
 * @typedef {Object} CaptureDraftContent
 * @property {string} [title]
 * @property {string} [project]
 * @property {string} [module]
 * @property {string} [repository]
 * @property {FactBlock[]} [facts]
 * @property {Artifact[]} [artifacts]
 */

/**
 * @typedef {Object} CaptureDraft
 * @property {number} id
 * @property {number} eventId
 * @property {CaptureDraftContent} draftContent
 * @property {number} reviewStatus
 */

export {}
