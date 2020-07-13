/*
 * Copyright (C) 2020 Dremio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dremio.nessie.services.rest;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;
import com.dremio.nessie.auth.User;
import com.dremio.nessie.backend.BranchController;
import com.dremio.nessie.model.Branch;
import com.dremio.nessie.model.CommitMeta;
import com.dremio.nessie.model.CommitMeta.Action;
import com.dremio.nessie.model.ImmutableCommitMeta;
import com.dremio.nessie.model.ImmutableTable;
import com.dremio.nessie.model.Table;
import com.dremio.nessie.services.auth.Secured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Principal;
import java.text.ParseException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import javax.annotation.security.RolesAllowed;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST endpoint for CRUD operations on tables.
 */
@ApplicationScoped
@Path("objects")
@SecurityScheme(
    name = "nessie-auth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
@SuppressWarnings("LineLength")
public class TableBranchOperations {
  //todo git like log

  private static final Logger logger = LoggerFactory.getLogger(TableBranchOperations.class);
  private final BranchController backend;

  @Inject
  public TableBranchOperations(BranchController backend) {
    this.backend = backend;
  }

  /**
   * get all branches.
   */
  @GET
  @Metered
  @ExceptionMetered(name = "exception-readall")
  @Secured
  @RolesAllowed({"admin", "user"})
  @Timed(name = "timed-readall")
  @Produces(MediaType.APPLICATION_JSON)
  @Operation(summary = "Fetch all branches endpoint",
      tags = {"get", "branch"},
      security = @SecurityRequirement(name = "nessie-auth"),
      responses = {
        @ApiResponse(
          description = "All known branches",
          content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = Branch[].class))),
        @ApiResponse(responseCode = "500", description = "Could not fetch data from backend")}
  )
  public Response tags() {
    try {
      return Response.ok(backend.getBranches().toArray(new Branch[0])).build();
    } catch (IOException e) {
      return exception(e);
    }
  }

  /**
   * get all tables in a branch.
   */
  @GET
  @Metered
  @ExceptionMetered(name = "exception-readall-tables")
  @Secured
  @RolesAllowed({"admin", "user"})
  @Timed(name = "timed-readall-tables")
  @Produces(MediaType.APPLICATION_JSON)
  @Path("{branch}/tables")
  @Operation(summary = "Fetch all tables on a branch endpoint",
      tags = {"get", "table"},
      security = @SecurityRequirement(name = "nessie-auth"),
      responses = {
        @ApiResponse(
          description = "all tables on branch",
          content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = String[].class))),
        @ApiResponse(responseCode = "404", description = "Branch not found"),
        @ApiResponse(responseCode = "500", description = "Could not fetch data from backend")}
  )
  public Response branch(@Parameter(description = "name of branch to fetch from", required = true)
                           @PathParam("branch") String branchName,
                         @Parameter(description = "filter for namespace")
                           @DefaultValue("all") @QueryParam("namespace") String namespace) {
    try {
      Branch branch = backend.getBranch(branchName);
      if (branch == null) {
        return Response.status(404).entity("branch not found").build();
      }
      List<String> tableList = backend.getTables(branch.getId(), namespace.equals("all")
          ? null : namespace);
      return Response.ok(tableList.toArray(new String[0])).build();
    } catch (IOException e) {
      return exception(e);
    }
  }

  /**
   * get branch details.
   */
  @GET
  @Metered
  @ExceptionMetered(name = "exception-readall-branches")
  @Secured
  @RolesAllowed({"admin", "user"})
  @Timed(name = "timed-readall-branches")
  @Produces(MediaType.APPLICATION_JSON)
  @Path("{branch}")
  @Operation(summary = "Fetch details of a branch endpoint",
      tags = {"get", "branch"},
      security = @SecurityRequirement(name = "nessie-auth"),
      responses = {
        @ApiResponse(
          description = "Branch details",
          content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = Branch.class))),
        @ApiResponse(responseCode = "404", description = "Branch not found"),
        @ApiResponse(responseCode = "500", description = "Could not fetch data from backend")}
  )
  public Response branchTables(@Parameter(description = "name of branch to fetch", required = true)
                                 @PathParam("branch") String branchName) {
    try {
      Branch branch = backend.getBranch(branchName);
      if (branch == null) {
        return Response.status(404).entity("branch not found").build();
      }
      return Response.ok(branch).tag(tagFromTable(branch)).build();
    } catch (IOException e) {
      return exception(e);
    }
  }

  private static EntityTag tagFromTable(String obj) {
    return new EntityTag(obj);
  }

  private static EntityTag tagFromTable(Branch obj) {
    return new EntityTag(obj.getId());
  }

  /**
   * get a table in a specific branch.
   */
  @GET
  @Metered
  @ExceptionMetered(name = "exception-readall-table")
  @Secured
  @RolesAllowed({"admin", "user"})
  @Timed(name = "timed-readall-table")
  @Produces(MediaType.APPLICATION_JSON)
  @Path("{branch}/{table}")
  @Operation(summary = "Fetch details of a table endpoint",
      tags = {"get", "table"},
      security = @SecurityRequirement(name = "nessie-auth"),
      responses = {
        @ApiResponse(
          description = "Details of table on branch",
          content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = Table.class))),
        @ApiResponse(responseCode = "404", description = "Table not found on branch"),
        @ApiResponse(responseCode = "500", description = "Could not fetch data from backend")}
  )
  public Response branchTable(@Parameter(description = "name of branch to search on", required = true)
                                @PathParam("branch") String branch,
                              @Parameter(description = "table name to search for", required = true)
                                @PathParam("table") String tableName,
                              @Parameter(description = "fetch all metadata on table")
                                @DefaultValue("false") @QueryParam("metadata") boolean metadata) {
    try {
      Table table = backend.getTable(branch, tableName, metadata);
      if (table == null) {
        return Response.status(404).entity("table not found on branch").build();
      }
      return Response.ok(table).build();
    } catch (IOException e) {
      return exception(e);
    }
  }

  /**
   * create a branch.
   */
  @SuppressWarnings("LineLength")
  @POST
  @Metered
  @ExceptionMetered(name = "exception-readall-branch")
  @Secured
  @RolesAllowed({"admin"})
  @Timed(name = "timed-readall-branch")
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("{branch}")
  @Operation(summary = "create branch endpoint",
      tags = {"post", "branch"},
      security = @SecurityRequirement(name = "nessie-auth"),
      responses = {
        @ApiResponse(responseCode = "409", description = "Branch already exists"),
        @ApiResponse(responseCode = "500", description = "Could not fetch data from backend")}
  )
  public Response createBranch(@Parameter(description = "name of branch to be created", required = true)
                                 @PathParam("branch") String branchName,
                               @Context SecurityContext securityContext,
                               @Parameter(description = "reason for this action for audit purposes")
                                 @DefaultValue("unknown") @QueryParam("reason") String reason,
                               @RequestBody(description = "branch object to be created",
                               content = @Content(schema = @Schema(implementation = Branch.class)))
                                   Branch branch) {
    try {
      if (backend.getBranch(branchName) != null) {
        return Response.status(409).entity("branch " + branch + " already exist").build();
      }
      Branch newBranch = backend.create(branchName,
                                        branch.getId(),
                                        meta(securityContext.getUserPrincipal(),
                                             reason,
                                             1,
                                             branchName,
                                             Action.CREATE_BRANCH));
      return Response.created(null).tag(tagFromTable(newBranch)).build();
    } catch (IOException e) {
      return exception(e);
    }

  }

  /**
   * create a table on a specific branch.
   */
  @POST
  @Metered
  @ExceptionMetered(name = "exception-create-table")
  @Secured
  @RolesAllowed({"admin"})
  @Timed(name = "timed-create-table")
  @Path("{branch}/{table}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Operation(summary = "create table on branch endpoint",
      tags = {"post", "table"},
      security = @SecurityRequirement(name = "nessie-auth"),
      responses = {
        @ApiResponse(responseCode = "404", description = "Branch doesn't exists"),
        @ApiResponse(responseCode = "400", description = "Table already exists"),
        @ApiResponse(responseCode = "412", description = "update conflict, tag out of date"),
        @ApiResponse(responseCode = "500", description = "Could not fetch data from backend")}
  )
  public Response createTable(@Parameter(description = "branch on which table will be created", required = true)
                                @PathParam("branch") String branch,
                              @Parameter(description = "name of table to be created", required = true)
                                @PathParam("table") String tableName,
                              @Parameter(description = "reason for this action for audit purposes")
                                @DefaultValue("unknown") @QueryParam("reason") String reason,
                              @Context SecurityContext securityContext,
                              @Context HttpHeaders headers,
                              @RequestBody(description = "table object to be created",
                                content = @Content(schema = @Schema(implementation = Table.class)))
                                  Table table) {
    try {
      if (backend.getBranch(branch) == null) {
        return Response.status(404).entity("branch " + branch + " does not exist").build();
      }
      if (backend.getTable(branch, tableName, false) != null) {
        return Response.status(404)
                       .entity("table " + tableName + " already exists on " + branch)
                       .build();
      }
    } catch (IOException e) {
      return exception(e);
    }
    return singleCommit(branch, tableName, securityContext, headers, table, reason, true);
  }

  private Response singleCommit(String branch,
                                String tableName,
                                SecurityContext securityContext,
                                HttpHeaders headers,
                                Table table,
                                String reason,
                                boolean post) {
    String ifMatch = version(headers);
    if (ifMatch == null) {
      return Response.status(412, "Tag not up to date").build();
    }
    Principal principal = securityContext.getUserPrincipal();
    return update(tableName, branch, table, principal, ifMatch, reason, post);
  }

  /**
   * delete a branch.
   */
  @DELETE
  @Metered
  @ExceptionMetered(name = "exception-delete-branch")
  @Secured
  @RolesAllowed({"admin"})
  @Timed(name = "timed-delete-branch")
  @Path("{branch}")
  @Operation(summary = "delete branch endpoint",
      tags = {"delete", "branch"},
      security = @SecurityRequirement(name = "nessie-auth"),
      responses = {
        @ApiResponse(responseCode = "404", description = "Branch doesn't exists"),
        @ApiResponse(responseCode = "412", description = "update conflict, tag out of date"),
        @ApiResponse(responseCode = "500", description = "Could not fetch data from backend")}
  )
  public Response deleteBranch(@Parameter(description = "branch to delete", required = true)
                                 @PathParam("branch") String branch,
                               @Parameter(description = "purge all data about branch")
                                 @DefaultValue("false") @QueryParam("purge") boolean purge,
                               @Parameter(description = "reason for this action for audit purposes")
                                 @DefaultValue("unknown") @QueryParam("reason") String reason,
                               @Context SecurityContext securityContext,
                               @Context HttpHeaders headers) {
    try {
      if (backend.getBranch(branch) == null) {
        return Response.status(404).entity("branch " + branch + " does not exist").build();
      }
      String ifMatch = version(headers);
      if (ifMatch == null) {
        return Response.status(412, "Tag not up to date").build();
      }
      backend.deleteBranch(branch, ifMatch, meta(securityContext.getUserPrincipal(),
                                                 reason,
                                                 1,
                                                 branch,
                                                 Action.DELETE_BRANCH));
      return Response.ok().build();
    } catch (IOException e) {
      return exception(e);
    } catch (IllegalStateException e) {
      return Response.status(412, "Tag not up to date" + exceptionString(e)).build();
    }
  }

  /**
   * delete a single table.
   */
  @DELETE
  @Metered
  @ExceptionMetered(name = "exception-delete-table")
  @Secured
  @RolesAllowed({"admin"})
  @Timed(name = "timed-delete-table")
  @Path("{branch}/{table}")
  @Operation(summary = "delete table on branch endpoint",
      tags = {"delete", "table"},
      security = @SecurityRequirement(name = "nessie-auth"),
      responses = {
        @ApiResponse(responseCode = "404", description = "Branch/table doesn't exists"),
        @ApiResponse(responseCode = "412", description = "update conflict, tag out of date"),
        @ApiResponse(responseCode = "500", description = "Could not fetch data from backend")}
  )
  public Response deleteTable(@Parameter(description = "branch on which to delete table", required = true)
                                @PathParam("branch") String branch,
                              @Parameter(description = "table to delete", required = true)
                                @PathParam("table") String table,
                              @Parameter(description = "reason for this action for audit purposes")
                                @DefaultValue("unknown") @QueryParam("reason") String reason,
                              @Parameter(description = "purge all data about branch")
                                @DefaultValue("false") @QueryParam("purge") boolean purge,
                              @Context SecurityContext securityContext,
                              @Context HttpHeaders headers) {
    try {
      if (backend.getBranch(branch) == null) {
        return Response.status(404).entity("branch " + branch + " does not exist").build();
      }
      Table branchTable = backend.getTable(branch, table, false);
      if (branchTable == null) {
        return Response.status(404)
                       .entity("table " + table + " does not exists on " + branch)
                       .build();
      }
      String ifMatch = version(headers);
      if (ifMatch == null) {
        return Response.status(412, "Tag not up to date").build();
      }
      ImmutableTable deletedTable = ImmutableTable.builder()
                                                  .from(branchTable)
                                                  .isDeleted(true)
                                                  .build();
      backend.commit(branch, meta(securityContext.getUserPrincipal(),
                                  reason + ";" + table,
                                  1,
                                  branch,
                                  Action.COMMIT
      ), ifMatch, deletedTable);
      return Response.ok().build();
    } catch (IOException e) {
      return exception(e);
    } catch (IllegalStateException e) {
      return Response.status(412, "Tag not up to date" + exceptionString(e)).build();
    }
  }

  /**
   * cherry pick mergeBranch onto branch.
   */
  @PUT
  @Metered
  @ExceptionMetered(name = "exception-cherry-pick")
  @Secured
  @RolesAllowed({"admin"})
  @Timed(name = "timed-cherry-pick")
  @Path("{branch}/cherry-pick")
  @Operation(summary = "cherry pick commits from mergeBranch to branch endpoint",
      tags = {"put", "commit"},
      security = @SecurityRequirement(name = "nessie-auth"),
      responses = {
        @ApiResponse(responseCode = "401", description = "no merge branch supplied"),
        @ApiResponse(responseCode = "404", description = "Branch doesn't exists"),
        @ApiResponse(responseCode = "412", description = "update conflict, tag out of date"),
        @ApiResponse(responseCode = "500", description = "Could not fetch data from backend")}
  )
  public Response cpBranch(@Parameter(description = "branch on which to add merges", required = true)
                             @PathParam("branch") String branch,
                           @Context SecurityContext securityContext,
                           @Context HttpHeaders headers,
                           @Parameter(description = "reason for this action for audit purposes")
                             @DefaultValue("unknown") @QueryParam("reason") String reason,
                           @Parameter(description = "name of branch to take commits from", required = true)
                             @QueryParam("promote") String mergeBranch,
                           @Parameter(description = "optional namespace, only tables on this namespace will be changed")
                             @QueryParam("namespace") String namespace) {
    try {
      if (mergeBranch == null) {
        return Response.status(401).entity("branch to cherry pick from is null").build();
      }
      if (backend.getBranch(branch) == null) {
        return Response.status(404).entity("branch " + branch + " does not exist").build();
      }
      String ifMatch = version(headers);
      if (ifMatch == null) {
        return Response.status(412, "Tag not up to date").build();
      }
      String result = backend.promote(branch,
                                      mergeBranch,
                                      ifMatch,
                                      meta(securityContext.getUserPrincipal(),
                                           reason + ";" + mergeBranch,
                                           1,
                                           branch,
                                           Action.CHERRY_PICK),
                                      false,
                                      true,
                                      namespace);
      return Response.ok().tag(tagFromTable(result)).build();
    } catch (IOException e) {
      return exception(e);
    } catch (IllegalStateException e) {
      return Response.status(412, "Tag not up to date" + exceptionString(e)).build();
    }
  }

  /**
   * merge mergeBranch onto branch, optionally forced.
   */
  @PUT
  @Metered
  @ExceptionMetered(name = "exception-merge")
  @Secured
  @RolesAllowed({"admin"})
  @Timed(name = "timed-merge")
  @Path("{branch}/promote")
  @Operation(summary = "merge commits from mergeBranch to branch endpoint",
      tags = {"put", "commit"},
      security = @SecurityRequirement(name = "nessie-auth"),
      responses = {
        @ApiResponse(responseCode = "401", description = "no merge branch supplied"),
        @ApiResponse(responseCode = "404", description = "Branch doesn't exists"),
        @ApiResponse(responseCode = "412", description = "update conflict, tag out of date"),
        @ApiResponse(responseCode = "500", description = "Could not fetch data from backend")}
  )
  public Response promoteBranch(@Parameter(description = "branch on which to add merges", required = true)
                           @PathParam("branch") String branch,
                           @Context SecurityContext securityContext,
                           @Context HttpHeaders headers,
                           @Parameter(description = "reason for this action for audit purposes")
                           @DefaultValue("unknown") @QueryParam("reason") String reason,
                           @Parameter(description = "name of branch to take commits from", required = true)
                           @QueryParam("promote") String mergeBranch,
                           @Parameter(description = "optional force, this will potentially delete history")
                                @DefaultValue("false") @QueryParam("force") boolean force) {
    try {
      if (mergeBranch == null) {
        return Response.status(401).entity("branch to merge from is null").build();
      }
      if (backend.getBranch(branch) == null) {
        return Response.status(404).entity("branch " + branch + " does not exist").build();
      }
      String ifMatch = version(headers);
      if (ifMatch == null) {
        return Response.status(412, "Tag not up to date").build();
      }
      String result = backend.promote(branch,
                                      mergeBranch,
                                      ifMatch,
                                      meta(securityContext.getUserPrincipal(),
                                           reason + ";" + mergeBranch,
                                           1,
                                           branch,
                                           force ? Action.FORCE_MERGE : Action.MERGE),
                                      force,
                                      false,
                                      null);
      return Response.ok().tag(tagFromTable(result)).build();
    } catch (IOException e) {
      return exception(e);
    } catch (IllegalStateException e) {
      return Response.status(412, "Tag not up to date" + exceptionString(e)).build();
    }
  }

  /**
   * update a list of tables on a given branch.
   */
  @PUT
  @Metered
  @ExceptionMetered(name = "exception-commit")
  @Secured
  @RolesAllowed({"admin"})
  @Timed(name = "timed-commit")
  @Path("{branch}")
  @Operation(summary = "commit tables to branch endpoint",
      tags = {"put", "commit"},
      security = @SecurityRequirement(name = "nessie-auth"),
      responses = {
        @ApiResponse(responseCode = "404", description = "Branch doesn't exists"),
        @ApiResponse(responseCode = "412", description = "update conflict, tag out of date"),
        @ApiResponse(responseCode = "500", description = "Could not fetch data from backend")}
  )
  public Response updateBatch(@Parameter(description = "branch on which to add merges", required = true)
                                @PathParam("branch") String branch,
                              @Context SecurityContext securityContext,
                              @Context HttpHeaders headers,
                              @Parameter(description = "reason for this action for audit purposes")
                                @DefaultValue("unknown") @QueryParam("reason") String reason,
                              @RequestBody(description = "table objects to be created, updated or deleted",
                                content = @Content(schema = @Schema(implementation = Table[].class)))
                                  Table[] batchUpdate) {
    try {
      if (backend.getBranch(branch) == null) {
        return Response.status(404).entity("branch " + branch + " does not exist").build();
      }
      String ifMatch = version(headers);
      if (ifMatch == null) {
        return Response.status(412, "Tag not up to date").build();
      }
      String headVersion = backend.commit(branch,
                                          meta(securityContext.getUserPrincipal(),
                                            reason,
                                            batchUpdate.length,
                                            branch,
                                            Action.COMMIT),
                                          ifMatch,
                                          batchUpdate);
      return Response.ok().tag(tagFromTable(headVersion)).build();
    } catch (IOException e) {
      return exception(e);
    } catch (IllegalStateException e) {
      return Response.status(412, "Tag not up to date" + exceptionString(e)).build();
    }
  }

  /**
   * update a single table on a branch.
   */
  @PUT
  @Metered
  @ExceptionMetered(name = "exception-commit-table")
  @Secured
  @RolesAllowed({"admin"})
  @Timed(name = "timed-commit-table")
  @Path("{branch}/{table}")
  @Operation(summary = "update via commit single table to branch endpoint",
      tags = {"put", "commit"},
      security = @SecurityRequirement(name = "nessie-auth"),
      responses = {
        @ApiResponse(responseCode = "404", description = "Branch/table doesn't exists"),
        @ApiResponse(responseCode = "412", description = "update conflict, tag out of date"),
        @ApiResponse(responseCode = "500", description = "Could not fetch data from backend")}
  )
  public Response update(@Parameter(description = "branch on which to add merges", required = true)
                           @PathParam("branch") String branch,
                         @Parameter(description = "table which will be changed", required = true)
                           @PathParam("table") String table,
                         @Parameter(description = "reason for this action for audit purposes")
                           @DefaultValue("unknown") @QueryParam("reason") String reason,
                         @Context SecurityContext securityContext,
                         @Context HttpHeaders headers,
                         Table update) {
    try {
      if (backend.getBranch(branch) == null) {
        return Response.status(404).entity("branch " + branch + " does not exist").build();
      }
      if (backend.getTable(branch, table, false) == null) {
        return Response.status(404)
                       .entity("table " + table + " does not exists on " + branch)
                       .build();
      }
    } catch (IOException e) {
      return exception(e);
    }
    return singleCommit(branch, table, securityContext, headers, update, reason, false);
  }

  private Response update(String table,
                          String branch,
                          Table branchTable,
                          Principal principal,
                          String ifMatch,
                          String reason,
                          boolean post) {
    if (!table.equals(branchTable.getId())) {
      return Response.status(404)
                     .entity("Can't update this table, table update is not correct")
                     .build();
    }
    try {
      String headVersion = backend.commit(branch,
                                          meta(principal,
                                               reason + ";" + table,
                                               1,
                                               branch,
                                               Action.COMMIT),
                                          ifMatch,
                                          branchTable);
      if (post) {
        return Response.created(null).tag(tagFromTable(headVersion)).build(); //todo uri
      }
      return Response.ok().tag(tagFromTable(headVersion)).build();
    } catch (IOException e) {
      return exception(e);
    } catch (IllegalStateException e) {
      return Response.status(412, "Tag not up to date" + exceptionString(e)).build();
    }
  }

  private static String version(HttpHeaders headers) {
    try {
      String ifMatch = headers.getHeaderString(HttpHeaders.IF_MATCH);
      return EntityTag.valueOf(ifMatch).getValue();
    } catch (NullPointerException | NoSuchElementException e) {
      return null;
    }
  }

  private static String exceptionString(Exception e) {
    StringWriter sw = new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }

  private static Response exception(Exception e) {
    String exceptionAsString = exceptionString(e);
    return Response.status(500)
                   .entity(Entity.entity(exceptionAsString, MediaType.APPLICATION_JSON_TYPE))
                   .build();
  }

  private CommitMeta meta(Principal principal,
                          String comment,
                          int changes,
                          String branch,
                          Action action) {
    return ImmutableCommitMeta.builder()
                              .email(email(principal))
                              .commiter(name(principal))
                              .comment(comment)
                              .changes(changes)
                              .branch(branch)
                              .action(action)
                              .build();
  }

  private String name(Principal principal) {
    return principal == null ? "" : principal.getName();
  }

  private String email(Principal principal) {
    try {
      User user = (User) principal;
      String email = user.email();
      return email == null ? "" : email;
    } catch (Exception e) {
      logger.warn("unable to cast principal {} to user and retrieve email", principal);
      return "";
    }
  }
}
