/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.sdk.kotlin.runtime.config.profile

/**
 * Describes a function that attempts to parse a line into a [Token] or returns null on failure.
 *
 * The implemented parse functions will fail to tokenize otherwise-valid lines depending on the passed parser state. For
 * example, a valid property definition line will not be recognized if a valid profile line has not been encountered
 * first.
 */
internal typealias ParseFn = (input: FileLine, currentProfile: Token.Profile?, lastProperty: Token.Property?) -> Token?

/**
 * Format (Configuration Files): [ Whitespace? profile Whitespace Identifier Whitespace? ] Whitespace? CommentLine?
 */
internal fun configurationProfile(input: FileLine, currentProfile: Token.Profile?, lastProperty: Token.Property?): Token.Profile? {
    if (!input.isProfile()) return null

    val parts = input.content
        .stripInlineComments()
        .stripComments()
        .drop(1)
        .dropLast(1)
        .splitWhitespace(limit = 2)

    val isValid = parts.size == 1 && parts[0] == Literals.DEFAULT_PROFILE ||
        parts.size == 2 && parts[0] == Literals.PROFILE_KEYWORD && parts[1].isValidIdentifier()
    return Token.Profile(parts.firstOrNull() == Literals.PROFILE_KEYWORD, parts.lastOrNull() ?: "", isValid)
}

/**
 * Format (Credentials Files): [ Whitespace? Identifier Whitespace? ] Whitespace? CommentLine?
 */
internal fun credentialProfile(input: FileLine, currentProfile: Token.Profile?, lastProperty: Token.Property?): Token.Profile? {
    if (!input.isProfile()) return null

    val name = input.content
        .stripInlineComments()
        .stripComments()
        .drop(1)
        .dropLast(1)
        .trim()

    return Token.Profile(false, name, name.isValidIdentifier())
}

/**
 * Format: Identifier Whitespace? = Whitespace? Value? Whitespace? (Whitespace CommentLine)?
 *
 * Defines a top-level property as part of a profile. Property values MAY be empty.
 */
internal fun property(input: FileLine, currentProfile: Token.Profile?, lastProperty: Token.Property?): Token.Property? {
    if (!input.isProperty()) return null
    if (currentProfile == null) return null

    val (key, value) = input.content.splitProperty()
    return Token.Property(key, value.stripInlineComments())
}

/**
 * Format: Whitespace Value Whitespace?
 *
 * Appends to a previously-defined (non-empty) property.
 */
internal fun continuation(input: FileLine, currentProfile: Token.Profile?, lastProperty: Token.Property?): Token.Continuation? {
    if (!input.isContinuation()) return null
    if (lastProperty == null || lastProperty.value.isEmpty()) return null

    return Token.Continuation(input.content.trim()) // inline comments are NOT stripped
}

/**
 * Format: Whitespace Identifier Whitespace? = Whitespace? Value Whitespace?
 *
 * A [continuation] that satisfies the following extra conditions:
 * - matches the syntax of a property definition (excluding the leading whitespace)
 * - follows a property definition with an empty value
 *
 * Parsed like a normal property definition, except any attempt at an inline comment is treated as part of the value.
 */
internal fun subProperty(input: FileLine, currentProfile: Token.Profile?, lastProperty: Token.Property?): Token.SubProperty? {
    if (!input.isSubProperty()) return null
    if (lastProperty == null || lastProperty.value.isNotEmpty()) return null

    val (key, value) = input.content.splitProperty()
    return Token.SubProperty(key, value) // inline comments are NOT stripped
}