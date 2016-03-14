/*
 * *** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2013
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.wado;

import org.dcm4che3.data.*;
import org.dcm4che3.io.*;
import org.dcm4che3.json.JSONWriter;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.TransferCapability;
import org.dcm4che3.util.StringUtils;
import org.dcm4che3.ws.rs.MediaTypes;
import org.dcm4chee.arc.conf.ArchiveAEExtension;
import org.dcm4chee.arc.conf.ArchiveAttributeCoercion;
import org.dcm4chee.arc.retrieve.*;
import org.jboss.resteasy.plugins.providers.multipart.MultipartRelatedOutput;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.*;
import javax.xml.transform.Templates;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @since Mar 2016
 */
@RequestScoped
@Path("aets/{AETitle}/rs")
public class WadoRS {

    private static final Logger LOG = LoggerFactory.getLogger(WadoRS.class);

    @Inject
    private RetrieveService service;

    private HttpServletRequest request;

    @Context
    private UriInfo uriInfo;

    @Context
    private HttpHeaders headers;

    @Inject
    private Device device;

    @Inject @RetrieveStart
    private Event<RetrieveContext> retrieveStart;

    @Inject @RetrieveEnd
    private Event<RetrieveContext> retrieveEnd;

    @PathParam("AETitle")
    private String aet;

    private Collection<String> acceptableTransferSyntaxes;

    @Override
    public String toString() {
        return request.getRequestURI();
    }

    @GET
    @Path("/studies/{studyUID}")
    @Produces("multipart/related;type=application/dicom")
    public void retrieveStudy(
            @PathParam("studyUID") String studyUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveStudy", studyUID, null, null, ar, Output.DICOM);
    }

    @GET
    @Path("/studies/{studyUID}")
    @Produces("multipart/related")
    public void retrieveStudyBulkdata(
            @PathParam("studyUID") String studyUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveStudyBulkdata", studyUID, null, null, ar, Output.BULKDATA);
    }

    @GET
    @Path("/studies/{studyUID}/render")
    @Produces("multipart/related")
    public void retrieveStudyRendered(
            @PathParam("studyUID") String studyUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveStudyBulkdata", studyUID, null, null, ar, Output.RENDER);
    }

    @GET
    @Path("/studies/{studyUID}/metadata")
    @Produces("application/json")
    public void retrieveStudyMetadataAsJSON(
            @PathParam("studyUID") String studyUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveStudyMetadataAsJSON", studyUID, null, null, ar, Output.METADATA_JSON);
    }

    @GET
    @Path("/studies/{studyUID}/metadata")
    @Produces("multipart/related;type=application/dicom+xml")
    public void retrieveStudyMetadataAsXML(
            @PathParam("studyUID") String studyUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveStudyMetadataAsXML", studyUID, null, null, ar, Output.METADATA_XML);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}")
    @Produces("multipart/related;type=application/dicom")
    public void retrieveSeries(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveSeries", studyUID, seriesUID, null, ar, Output.DICOM);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}")
    @Produces("multipart/related")
    public void retrieveSeriesBulkdata(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveSeriesBulkdata", studyUID, seriesUID, null, ar, Output.BULKDATA);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/render")
    @Produces("multipart/related")
    public void retrieveSeriesRendered(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveSeriesRendered", studyUID, seriesUID, null, ar, Output.RENDER);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/metadata")
    @Produces("application/json")
    public void retrieveSeriesMetadataAsJSON(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveSeriesMetadataAsJSON", studyUID, seriesUID, null, ar, Output.METADATA_JSON);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/metadata")
    @Produces("multipart/related;type=application/dicom+xml")
    public void retrieveSeriesMetadataAsXML(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveSeriesMetadataAsXML", studyUID, seriesUID, null, ar, Output.METADATA_XML);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}")
    @Produces("multipart/related;type=application/dicom")
    public void retrieveInstance(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveInstance", studyUID, seriesUID, objectUID, ar, Output.DICOM);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}")
    @Produces("multipart/related")
    public void retrieveInstanceBulkdata(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveInstanceBulkdata", studyUID, seriesUID, objectUID, ar, Output.BULKDATA);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}")
    @Produces("multipart/related")
    public void retrieveInstanceRender(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveInstanceRender", studyUID, seriesUID, objectUID, ar, Output.RENDER);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/metadata")
    @Produces("application/json")
    public void retrieveInstanceMetadataAsJSON(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveInstanceMetadataAsJSON", studyUID, seriesUID, objectUID, ar, Output.METADATA_JSON);
    }

    @GET
    @Path("/studies/{studyUID}/series/{seriesUID}/instances/{objectUID}/metadata")
    @Produces("multipart/related;type=application/dicom+xml")
    public void retrieveInstanceMetadataAsXML(
            @PathParam("studyUID") String studyUID,
            @PathParam("seriesUID") String seriesUID,
            @PathParam("objectUID") String objectUID,
            @Suspended AsyncResponse ar) {
        retrieve("retrieveInstanceMetadataAsXML", studyUID, seriesUID, objectUID, ar, Output.METADATA_XML);
    }

    private void retrieve(String method, String studyUID, String seriesUID, String objectUID, AsyncResponse ar,
                              Output output) {
        // @Inject does not work:
        // org.jboss.resteasy.spi.LoggableFailure: Unable to find contextual data of type: javax.servlet.http.HttpServletRequest
        // s. https://issues.jboss.org/browse/RESTEASY-903
        request = ResteasyProviderFactory.getContextData(HttpServletRequest.class);
        LOG.info("Process GET {} from {}@{}", this, request.getRemoteUser(), request.getRemoteHost());
        try {
            checkAET();
            final RetrieveContext ctx = service.newRetrieveContextWADO(request, aet, studyUID, seriesUID, objectUID);
            service.calculateMatches(ctx);
            LOG.info("{}: {} Matches", method, ctx.getNumberOfMatches());
            if (ctx.getNumberOfMatches() == 0)
                throw new WebApplicationException(Response.Status.NOT_FOUND);
            retrieveStart.fire(ctx);
            ar.register(new CompletionCallback() {
                @Override
                public void onComplete(Throwable throwable) {
                    ctx.setException(throwable);
                    retrieveEnd.fire(ctx);
                }
            });
            ar.resume(Response.status(output.status(this, ctx)).entity(output.entity(this, ctx)).build());
        } catch (Exception e) {
            ar.resume(e);
        }
    }

    private void checkAET() {
        ApplicationEntity ae = device.getApplicationEntity(aet, true);
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(
                    "No such Application Entity: " + aet,
                    Response.Status.SERVICE_UNAVAILABLE);
    }

    private enum Output {
        DICOM {
            @Override
            protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                                   InstanceLocations inst) {
                wadoRS.writeDICOM(output, ctx, inst);
            }
        },
        BULKDATA,
        RENDER,
        METADATA_XML {
            @Override
            protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                                   InstanceLocations inst) {
                wadoRS.writeMetadataXML(output, ctx, inst);
            }
        },
        METADATA_JSON {
            @Override
            public Object entity(WadoRS wadoRS, RetrieveContext ctx) {
                return wadoRS.writeMetadataJSON(ctx);
            }
        };

        public Response.Status status(WadoRS wadoRS, RetrieveContext ctx) {
            return Response.Status.OK;
        }

        public Object entity(WadoRS wadoRS, RetrieveContext ctx) {
            MultipartRelatedOutput output = new MultipartRelatedOutput();
            for (InstanceLocations inst : ctx.getMatches()) {
                addPart(output, wadoRS, ctx, inst);
            }
            return output;
        }

        protected void addPart(MultipartRelatedOutput output, WadoRS wadoRS, RetrieveContext ctx,
                               InstanceLocations inst) {
             throw new WebApplicationException(name() + " not implemented", Response.Status.SERVICE_UNAVAILABLE);
        }
    }

    private void writeDICOM(MultipartRelatedOutput output, RetrieveContext ctx, InstanceLocations inst)  {
        DicomObjectOutput entity = new DicomObjectOutput(ctx, inst, acceptableTransferSyntaxes());
        output.addPart(entity, MediaTypes.APPLICATION_DICOM_TYPE);
    }

    private Collection<String> acceptableTransferSyntaxes() {
        Collection<String> tsuids = acceptableTransferSyntaxes;
        if (tsuids == null) {
            tsuids = new HashSet<>();
            for (MediaType mediaType : headers.getAcceptableMediaTypes()) {
                tsuids.add(MediaTypes.getTransferSyntax(MediaTypes.getMultiPartRelatedType(mediaType)));
            }
            tsuids.remove(null);
            acceptableTransferSyntaxes = tsuids;
        }
        return tsuids;
    }

    private AttributesCoercion coercion(RetrieveContext ctx, InstanceLocations inst) throws Exception {
        ArchiveAEExtension aeExt = ctx.getArchiveAEExtension();
        ArchiveAttributeCoercion coercion = aeExt.findAttributeCoercion(
                request.getRemoteHost(), null, TransferCapability.Role.SCP, Dimse.C_STORE_RQ, inst.getSopClassUID());
        if (coercion == null)
            return null;
        LOG.debug("{}: apply {}", this, coercion);
        String uri = StringUtils.replaceSystemProperties(coercion.getXSLTStylesheetURI());
        Templates tpls = TemplatesCache.getDefault().get(uri);
        return new XSLTAttributesCoercion(tpls, null)
                .includeKeyword(!coercion.isNoKeywords());
    }

    private void writeMetadataXML(MultipartRelatedOutput output, final RetrieveContext ctx,
                                  final InstanceLocations inst) {
        output.addPart(
                new StreamingOutput() {
                    @Override
                    public void write(OutputStream out) throws IOException,
                            WebApplicationException {
                        try {
                            SAXTransformer.getSAXWriter(new StreamResult(out)).write(loadAttrsWithBulkdataURI(ctx, inst));
                        } catch (Exception e) {
                            throw new WebApplicationException(e);
                        }
                    }
                },
                MediaTypes.APPLICATION_DICOM_XML_TYPE);

    }

    private Object writeMetadataJSON(final RetrieveContext ctx) {
        final Collection<InstanceLocations> insts = ctx.getMatches();
        return new StreamingOutput() {
            @Override
            public void write(OutputStream out) throws IOException {
                try {
                    JsonGenerator gen = Json.createGenerator(out);
                    JSONWriter writer = new JSONWriter(gen);
                    gen.writeStartArray();
                    for (InstanceLocations inst : insts) {
                        writer.write(loadAttrsWithBulkdataURI(ctx, inst));
                    }
                    gen.writeEnd();
                    gen.flush();
                } catch (Exception e) {
                    throw new WebApplicationException(e);
                }
            }
        };
    }

    private Attributes loadAttrsWithBulkdataURI(RetrieveContext ctx, InstanceLocations inst) throws Exception {
        Attributes attrs;
        final ArrayList<BulkData> bulkDataList = new ArrayList<>();
        String bulkDataURI;
        try (DicomInputStream dis = service.openDicomInputStream(ctx, inst)) {
            dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.URI);
            dis.setBulkDataCreator(new BulkDataCreator() {
                @Override
                public BulkData createBulkData(DicomInputStream dis) throws IOException {
                    BulkData bulkData = new BulkData(null, dis.getAttributePath(), dis.bigEndian());
                    bulkDataList.add(bulkData);
                    return bulkData;
                }
            });
            attrs = dis.readDataset(-1, Tag.PixelData);
            bulkDataURI = mkBulkDataURI(attrs);
            if (dis.tag() == Tag.PixelData) {
                attrs.setValue(Tag.PixelData, dis.vr(), new BulkData(null, bulkDataURI, dis.bigEndian()));
            }
        }
        for (BulkData bulkData : bulkDataList) {
            bulkData.setURI(bulkDataURI + bulkData.getURI());
        }

        MergeAttributesCoercion coerce = new MergeAttributesCoercion(inst.getAttributes(), coercion(ctx, inst));
        coerce.coerce(attrs, null);
        return attrs;
    }

    private String mkBulkDataURI(Attributes attrs) {
        StringBuffer sb = request.getRequestURL();
        sb.setLength(sb.lastIndexOf("/metadata"));
        if (sb.lastIndexOf("/instances/") < 0) {
            if (sb.lastIndexOf("/series/") < 0) {
                sb.append("/series/").append(attrs.getString(Tag.SeriesInstanceUID));
            }
            sb.append("/instances/").append(attrs.getString(Tag.SOPInstanceUID));
        }
        return sb.toString();
    }

}