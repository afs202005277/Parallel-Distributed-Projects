# Parallel-Distributed-Projects

## Overview
This repository contains two projects developed as part of coursework for the Parallel and Distributed Computing (CPD) course at the Faculty of Engineering of the University of Porto. The projects focus on different aspects of distributed computing and performance evaluation.

## Projects

### 1. Assign1 - Performance Evaluation of a Single Core
**Description:** This project investigates how large amounts of data impact processor memory performance, with a focus on matrix multiplication. Three different methods of matrix multiplication were implemented and analyzed:
- **Column Multiplication:** The naive approach, where each element is computed independently.
- **Line Multiplication:** A more efficient approach that accumulates values progressively.
- **Block Multiplication:** A method that divides the matrix into blocks to optimize memory access patterns.

**Performance Metrics:**
- Execution time
- Cache misses in L1 and L2 caches

**Key Findings:**
- Column multiplication is the least efficient due to inefficient memory access patterns.
- Line multiplication is more "cache-friendly" and improves performance.
- Block multiplication further optimizes performance when block sizes are well chosen.
- A comparison between C/C++ and interpreted languages like JavaScript demonstrated the superior efficiency of compiled languages for such computations.

### 2. Assign2 - Distributed Systems: Client-Server Game System
**Description:** This project involves the implementation of a client-server system using TCP sockets in Java. The goal is to create an online text-based game system where users can authenticate, queue for a game, and play.

**Key Features:**
- **Matchmaking Modes:**
  - **Simple Mode:** Players are assigned to game instances in the order they connect.
  - **Ranked Mode:** Matchmaking is based on player skill levels.
- **Fault Tolerance:**
  - The system is designed to handle broken connections while users are in queue.
  - Players can resume sessions using unique tokens.
- **Concurrency:**
  - Uses Java's `java.utils.concurrent.locks` instead of built-in thread-safe collections.
  - Implements a thread pool for managing concurrent game sessions.
  - Uses non-blocking channels (`java.nio.channels`) to efficiently handle large numbers of connections.
- **Authentication & Registration:**
  - Supports user registration and authentication.
  - Allows users to persist login credentials for multiple game sessions.

## Requirements
- **Assign1:**
  - C/C++ compiler
  - JavaScript
  - A system supporting performance analysis tools for cache measurements
- **Assign2:**
  - Java SE 17 or newer
  - TCP socket support
  - No external Java libraries beyond Java SE


